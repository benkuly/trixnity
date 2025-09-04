package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.Level
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.crypto.ClientOlmStore
import net.folivo.trixnity.client.mocks.KeyServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.model.devices.DehydratedDeviceData
import net.folivo.trixnity.clientserverapi.model.devices.GetDehydratedDevice
import net.folivo.trixnity.clientserverapi.model.devices.GetDehydratedDeviceEvents
import net.folivo.trixnity.clientserverapi.model.devices.SetDehydratedDevice
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.RoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.INITIAL_PRE_KEY
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.AesHmacSha2EncryptedData
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.decryptAesHmacSha2
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(MSC3814::class)
class DehydratedDeviceServiceTest : TrixnityBaseTest() {
    override val packageLogLevels: Map<String, Level>
        get() = super.packageLogLevels + mapOf("net.folivo.trixnity.client.store.cache" to Level.INFO)

    private val roomId = RoomId("!room:server")
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val signServiceMock = SignServiceMock()
    private val keyStore = getInMemoryKeyStore()
    private val olmCryptoStore = getInMemoryOlmStore()
    private val olmStore = ClientOlmStore(
        accountStore = getInMemoryAccountStore(),
        olmCryptoStore = olmCryptoStore,
        keyStore = keyStore,
        roomStateStore = getInMemoryRoomStateStore(),
        loadMembersService = { _, _ -> },
    )

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val userInfo = UserInfo(
        alice,
        aliceDevice, Ed25519Key(aliceDevice, "ed25519Key"),
        Curve25519Key(aliceDevice, "curve25519Key")
    )

    private val json = createMatrixEventJson()

    @OptIn(ExperimentalSerializationApi::class)
    private val decryptedOlmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(DecryptedOlmEvent::class))
    private val matrixClientConfiguration = MatrixClientConfiguration()

    private val cut = DehydratedDeviceService(
        api = api,
        keyStore = keyStore,
        userInfo = userInfo,
        json = json,
        olmStore = olmStore,
        keyService = KeyServiceMock(),
        signService = signServiceMock,
        clock = testScope.testClock,
        config = matrixClientConfiguration,
    )

    @Test
    fun `dehydrateDevice should create a new dehydrated device`() = runTest {
        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        var setDehydratedDevice: SetDehydratedDevice.Request? = null

        val (_, selfSigningPrivateKey) = freeAfter(OlmPkSigning.create()) {
            it.publicKey to it.privateKey
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(SetDehydratedDevice()) {
                setDehydratedDevice = it
                val deviceId = it.deviceId
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        deviceId to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, deviceId, setOf(),
                                    keysOf()
                                ), mapOf()
                            ),
                            KeySignatureTrustLevel.CrossSigned(true)
                        )
                    )
                }
                SetDehydratedDevice.Response(deviceId)
            }
        }

        val dehydrateDeviceJob = launch {
            cut.tryDehydrateDevice(dehydratedDeviceKey)
        }
        delay(100.milliseconds)
        dehydrateDeviceJob.isActive shouldBe true
        keyStore.updateSecrets {
            mapOf(
                SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    ClientEvent.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                    selfSigningPrivateKey
                )
            )
        }
        delay(100.milliseconds)
        dehydrateDeviceJob.join()

        val deviceData =
            setDehydratedDevice?.deviceData.shouldBeInstanceOf<DehydratedDeviceData.DehydrationV2Compatibility>()
        val dehydratedDeviceAccount = OlmAccount.unpickle(
            null, decryptAesHmacSha2(
                content = with(deviceData) {
                    AesHmacSha2EncryptedData(
                        iv = iv,
                        ciphertext = encryptedDevicePickle,
                        mac = mac
                    )
                },
                key = dehydratedDeviceKey,
                name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
            ).decodeToString()
        )

        setDehydratedDevice.deviceId shouldBe dehydratedDeviceAccount.identityKeys.curve25519
        setDehydratedDevice.deviceKeys.shouldNotBeNull()
        setDehydratedDevice.oneTimeKeys.shouldNotBeNull()
        setDehydratedDevice.fallbackKeys.shouldNotBeNull()
    }

    @Test
    fun `rehydrateDevice should process a dehydrated device`() = runTest {
        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        val deviceId = "DEHYDRATED_DEVICE_ID"
        var getDehydratedDeviceCalled = false
        var getDehydratedDeviceEventsCalled = false

        val dehydratedDeviceAccount = OlmAccount.create()
        dehydratedDeviceAccount.generateOneTimeKeys(dehydratedDeviceAccount.maxNumberOfOneTimeKeys)

        val bobAccount = OlmAccount.create()

        val encryptedData = encryptAesHmacSha2(
            content = dehydratedDeviceAccount.pickle(null).encodeToByteArray(),
            key = dehydratedDeviceKey,
            name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
        )

        val deviceData = DehydratedDeviceData.DehydrationV2Compatibility(
            iv = encryptedData.iv,
            encryptedDevicePickle = encryptedData.ciphertext,
            mac = encryptedData.mac
        )

        val megolmSession = freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            RoomKeyEventContent(
                roomId = roomId,
                sessionId = outboundSession.sessionId,
                sessionKey = outboundSession.sessionKey,
                algorithm = EncryptionAlgorithm.Megolm,
            )
        }
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                dehydratedDeviceAccount.identityKeys.curve25519,
                dehydratedDeviceAccount.oneTimeKeys.curve25519.values.first()
            )
        ) { olmSession ->
            olmSession.encrypt(
                json.encodeToString(
                    decryptedOlmEventSerializer,
                    DecryptedOlmEvent(
                        content = megolmSession,
                        sender = bob,
                        senderKeys = keysOf(
                            Ed25519Key(null, bobAccount.identityKeys.ed25519)
                        ),
                        recipient = alice,
                        recipientKeys = keysOf(
                            Ed25519Key(null, dehydratedDeviceAccount.identityKeys.ed25519)
                        ),
                    )
                )
            )
        }
        val encryptedMegolmSession =
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(
                    dehydratedDeviceAccount.identityKeys.curve25519 to CiphertextInfo(
                        encryptedMessage.cipherText,
                        INITIAL_PRE_KEY
                    )
                ),
                senderKey = KeyValue.Curve25519KeyValue(bobAccount.identityKeys.curve25519)
            )

        keyStore.updateDeviceKeys(bob) {
            mapOf(
                "BOB_DEVICE" to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(
                            bob, "BOB_DEVICE", setOf(),
                            keysOf(
                                Ed25519Key("BOB_DEVICE", bobAccount.identityKeys.ed25519),
                                Curve25519Key("BOB_DEVICE", bobAccount.identityKeys.curve25519),
                            )
                        ), mapOf()
                    ),
                    Valid(false)
                )
            )
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice()) {
                getDehydratedDeviceCalled = true
                GetDehydratedDevice.Response(
                    deviceId = deviceId,
                    deviceData = deviceData
                )
            }
            matrixJsonEndpoint(GetDehydratedDeviceEvents(deviceId)) {
                getDehydratedDeviceEventsCalled = true
                if (it.nextBatch == null)
                    GetDehydratedDeviceEvents.Response(
                        events = listOf(ClientEvent.ToDeviceEvent(encryptedMegolmSession, bob)),
                        nextBatch = "nextBatch1"
                    )
                else
                    GetDehydratedDeviceEvents.Response(
                        events = listOf(),
                        nextBatch = "nextBatch2"
                    )

            }
        }

        dehydratedDeviceAccount.markKeysAsPublished()

        cut.tryRehydrateDevice(dehydratedDeviceKey)

        getDehydratedDeviceCalled shouldBe true
        getDehydratedDeviceEventsCalled shouldBe true
        olmStore.getInboundMegolmSession(
            megolmSession.sessionId,
            megolmSession.roomId
        ).shouldNotBeNull()

        olmStore.updateOlmSessions(KeyValue.Curve25519KeyValue(bobAccount.identityKeys.curve25519)) {
            it shouldBe null
        }
    }

    @Test
    fun `rehydrateDevice should handle not found error`() = runTest {
        var getDehydratedDeviceCalled = false

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice()) {
                getDehydratedDeviceCalled = true
                throw MatrixServerException(
                    HttpStatusCode.NotFound,
                    ErrorResponse.NotFound("no dehydrated device present")
                )
            }
        }

        cut.tryRehydrateDevice("testKey".encodeToByteArray())

        getDehydratedDeviceCalled shouldBe true
    }

    @Test
    fun `rehydrateDevice should ignore wrong key`() = runTest {
        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        val deviceId = "DEHYDRATED_DEVICE_ID"
        var getDehydratedDeviceCalled = false
        var getDehydratedDeviceEventsCalled = false

        val dehydratedDeviceAccount = OlmAccount.create()
        dehydratedDeviceAccount.generateOneTimeKeys(dehydratedDeviceAccount.maxNumberOfOneTimeKeys)

        val bobAccount = OlmAccount.create()

        val encryptedData = encryptAesHmacSha2(
            content = dehydratedDeviceAccount.pickle(null).encodeToByteArray(),
            key = dehydratedDeviceKey,
            name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
        )

        val deviceData = DehydratedDeviceData.DehydrationV2Compatibility(
            iv = encryptedData.iv,
            encryptedDevicePickle = encryptedData.ciphertext,
            mac = encryptedData.mac
        )

        val megolmSession = freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            RoomKeyEventContent(
                roomId = roomId,
                sessionId = outboundSession.sessionId,
                sessionKey = outboundSession.sessionKey,
                algorithm = EncryptionAlgorithm.Megolm,
            )
        }
        val encryptedMessage = freeAfter(
            OlmSession.createOutbound(
                bobAccount,
                dehydratedDeviceAccount.identityKeys.curve25519,
                dehydratedDeviceAccount.oneTimeKeys.curve25519.values.first()
            )
        ) { olmSession ->
            olmSession.encrypt(
                json.encodeToString(
                    decryptedOlmEventSerializer,
                    DecryptedOlmEvent(
                        content = megolmSession,
                        sender = bob,
                        senderKeys = keysOf(
                            Ed25519Key(null, bobAccount.identityKeys.ed25519)
                        ),
                        recipient = alice,
                        recipientKeys = keysOf(
                            Ed25519Key(null, dehydratedDeviceAccount.identityKeys.ed25519)
                        ),
                    )
                )
            )
        }
        val encryptedMegolmSession =
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(
                    dehydratedDeviceAccount.identityKeys.curve25519 to CiphertextInfo(
                        encryptedMessage.cipherText,
                        INITIAL_PRE_KEY
                    )
                ),
                senderKey = KeyValue.Curve25519KeyValue(bobAccount.identityKeys.curve25519)
            )

        keyStore.updateDeviceKeys(bob) {
            mapOf(
                "BOB_DEVICE" to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(
                            bob, "BOB_DEVICE", setOf(),
                            keysOf(
                                Ed25519Key("BOB_DEVICE", bobAccount.identityKeys.ed25519),
                                Curve25519Key("BOB_DEVICE", bobAccount.identityKeys.curve25519),
                            )
                        ), mapOf()
                    ),
                    Valid(false)
                )
            )
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice()) {
                getDehydratedDeviceCalled = true
                GetDehydratedDevice.Response(
                    deviceId = deviceId,
                    deviceData = deviceData
                )
            }
            matrixJsonEndpoint(GetDehydratedDeviceEvents(deviceId)) {
                getDehydratedDeviceEventsCalled = true
                if (it.nextBatch == null)
                    GetDehydratedDeviceEvents.Response(
                        events = listOf(ClientEvent.ToDeviceEvent(encryptedMegolmSession, bob)),
                        nextBatch = "nextBatch1"
                    )
                else
                    GetDehydratedDeviceEvents.Response(
                        events = listOf(),
                        nextBatch = "nextBatch2"
                    )

            }
        }

        dehydratedDeviceAccount.markKeysAsPublished()

        cut.tryRehydrateDevice(SecureRandom.nextBytes(32))

        getDehydratedDeviceCalled shouldBe true
        getDehydratedDeviceEventsCalled shouldBe false
        olmStore.getInboundMegolmSession(
            megolmSession.sessionId,
            megolmSession.roomId
        ) shouldBe null
    }
}
