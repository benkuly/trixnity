package de.connect2x.trixnity.client.key

import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.ClientOlmStore
import de.connect2x.trixnity.client.mocks.KeyServiceMock
import de.connect2x.trixnity.client.mocks.SignServiceMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.Valid
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.clientserverapi.model.device.DehydratedDeviceData
import de.connect2x.trixnity.clientserverapi.model.device.GetDehydratedDevice
import de.connect2x.trixnity.clientserverapi.model.device.GetDehydratedDeviceEvents
import de.connect2x.trixnity.clientserverapi.model.device.SetDehydratedDevice
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.DecryptedOlmEvent
import de.connect2x.trixnity.core.model.events.m.RoomKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent.CiphertextInfo
import de.connect2x.trixnity.core.model.keys.*
import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.crypto.core.AesHmacSha2EncryptedData
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.core.decryptAesHmacSha2
import de.connect2x.trixnity.crypto.core.encryptAesHmacSha2
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.keys.PickleKeyFactory
import de.connect2x.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import de.connect2x.trixnity.crypto.driver.olm.Account
import de.connect2x.trixnity.crypto.driver.olm.Message
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.crypto.of
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(MSC3814::class)
class DehydratedDeviceServiceTestVodozemac : DehydratedDeviceServiceTest(VodozemacCryptoDriver)

@OptIn(MSC3814::class)
class DehydratedDeviceServiceTestLibOlm : DehydratedDeviceServiceTest(LibOlmCryptoDriver)

@OptIn(MSC3814::class)
abstract class DehydratedDeviceServiceTest(
    protected val driver: CryptoDriver,
) : TrixnityBaseTest() {
    protected val roomId = RoomId("!room:server")
    protected val alice = UserId("alice", "server")
    protected val bob = UserId("bob", "server")
    protected val aliceDevice = "ALICEDEVICE"

    protected val signServiceMock = SignServiceMock()
    protected val keyStore = getInMemoryKeyStore()
    protected val olmCryptoStore = getInMemoryOlmStore()
    protected val olmStore = ClientOlmStore(
        accountStore = getInMemoryAccountStore(),
        olmCryptoStore = olmCryptoStore,
        keyStore = keyStore,
        roomStateStore = getInMemoryRoomStateStore(),
        loadMembersService = { _, _ -> },
    )

    protected val apiConfig = PortableMockEngineConfig()
    protected val api = mockMatrixClientServerApiClient(apiConfig)

    protected val userInfo = UserInfo(
        alice,
        aliceDevice, Ed25519Key(aliceDevice, "ed25519Key"),
        Curve25519Key(aliceDevice, "curve25519Key")
    )

    protected val json = createMatrixEventJson()

    @OptIn(ExperimentalSerializationApi::class)
    protected val decryptedOlmEventSerializer =
        requireNotNull(json.serializersModule.getContextual(DecryptedOlmEvent::class))
    protected val matrixClientConfiguration = MatrixClientConfiguration()

    protected val cut = DehydratedDeviceService(
        api = api,
        keyStore = keyStore,
        userInfo = userInfo,
        json = json,
        olmStore = olmStore,
        keyService = KeyServiceMock(),
        signService = signServiceMock,
        clock = testScope.testClock,
        config = matrixClientConfiguration,
        driver = driver,
    )

    @Test
    fun `rehydrateDevice should process a compatibility dehydrated device`() = runTest {
        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        val deviceId = "DEHYDRATED_DEVICE_ID"
        var getDehydratedDeviceCalled = false
        var getDehydratedDeviceEventsCalled = false

        val dehydratedDeviceAccount = driver.olm.account()
        dehydratedDeviceAccount.generateOneTimeKeys(dehydratedDeviceAccount.maxNumberOfOneTimeKeys)

        val bobAccount = driver.olm.account()

        val encryptedData = encryptAesHmacSha2(
            content = dehydratedDeviceAccount.pickle().encodeToByteArray(),
            key = dehydratedDeviceKey,
            name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
        )

        val deviceData = DehydratedDeviceData.DehydrationV2Compatibility(
            iv = encryptedData.iv,
            encryptedDevicePickle = encryptedData.ciphertext,
            mac = encryptedData.mac
        )

        rehydrateDevice(
            bobAccount,
            dehydratedDeviceAccount,
            deviceId,
            deviceData,
            dehydratedDeviceKey
        )
    }

    @Test
    fun `rehydrateDevice should process a dehydrated device`() = runTest {
        if (!driver.olm.account.dehydratedDevicesSupported) return@runTest

        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        val deviceId = "DEHYDRATED_DEVICE_ID"

        val dehydratedDeviceAccount = driver.olm.account()
        dehydratedDeviceAccount.generateOneTimeKeys(dehydratedDeviceAccount.maxNumberOfOneTimeKeys)

        val bobAccount = driver.olm.account()

        val pickleKey = checkNotNull(driver.key.pickleKey(dehydratedDeviceKey))

        val dehydratedDevice = dehydratedDeviceAccount.dehydrate(pickleKey)
        val deviceData = DehydratedDeviceData.DehydrationV2(
            devicePickle = dehydratedDevice.pickle,
            nonce = dehydratedDevice.nonce,
        )

        rehydrateDevice(
            bobAccount,
            dehydratedDeviceAccount,
            deviceId,
            deviceData,
            dehydratedDeviceKey
        )
    }

    private suspend fun rehydrateDevice(
        bobAccount: Account,
        dehydratedDeviceAccount: Account,
        deviceId: String,
        deviceData: DehydratedDeviceData,
        dehydratedDeviceKey: ByteArray
    ) {
        var getDehydratedDeviceCalled = false
        var getDehydratedDeviceEventsCalled = false
        val outboundSession = driver.megolm.groupSession()
        val megolmSession = RoomKeyEventContent(
            roomId = roomId,
            sessionId = outboundSession.sessionId,
            sessionKey = SessionKeyValue.of(outboundSession.sessionKey),
            algorithm = EncryptionAlgorithm.Megolm,
        )
        val olmSession = bobAccount.createOutboundSession(
            identityKey = dehydratedDeviceAccount.curve25519Key,
            oneTimeKey = dehydratedDeviceAccount.oneTimeKeys.values.first(),
        )
        val encryptedMessage = olmSession.encrypt(
            json.encodeToString(
                decryptedOlmEventSerializer, DecryptedOlmEvent(
                    content = megolmSession,
                    sender = bob,
                    senderKeys = keysOf(
                        Key.of(null, bobAccount.ed25519Key)
                    ),
                    recipient = alice,
                    recipientKeys = keysOf(
                        Key.of(null, dehydratedDeviceAccount.ed25519Key)
                    ),
                )
            )
        ) as Message.PreKey
        val encryptedMegolmSession =
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(
                    dehydratedDeviceAccount.curve25519Key.base64 to CiphertextInfo.of(encryptedMessage)
                ), senderKey = KeyValue.of(bobAccount.curve25519Key)
            )

        keyStore.updateDeviceKeys(bob) {
            mapOf(
                "BOB_DEVICE" to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(
                            bob, "BOB_DEVICE", setOf(),
                            keysOf(
                                Key.of("BOB_DEVICE", bobAccount.ed25519Key),
                                Key.of("BOB_DEVICE", bobAccount.curve25519Key),
                            )
                        ), mapOf()
                    ),
                    Valid(false)
                )
            )
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice) {
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

        olmStore.updateOlmSessions(KeyValue.of(bobAccount.curve25519Key)) {
            it shouldBe null
        }
    }

    @Test
    fun `rehydrateDevice should handle not found error`() = runTest {
        var getDehydratedDeviceCalled = false

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice) {
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

        val dehydratedDeviceAccount = driver.olm.account()
        dehydratedDeviceAccount.generateOneTimeKeys(dehydratedDeviceAccount.maxNumberOfOneTimeKeys)

        val bobAccount = driver.olm.account()

        val encryptedData = encryptAesHmacSha2(
            content = dehydratedDeviceAccount.pickle().encodeToByteArray(),
            key = dehydratedDeviceKey,
            name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
        )

        val deviceData = DehydratedDeviceData.DehydrationV2Compatibility(
            iv = encryptedData.iv,
            encryptedDevicePickle = encryptedData.ciphertext,
            mac = encryptedData.mac
        )

        val megolmSession = driver.megolm.groupSession().use { outboundSession ->
            RoomKeyEventContent(
                roomId = roomId,
                sessionId = outboundSession.sessionId,
                sessionKey = SessionKeyValue.of(outboundSession.sessionKey),
                algorithm = EncryptionAlgorithm.Megolm,
            )
        }
        val encryptedMessage = bobAccount.createOutboundSession(
            dehydratedDeviceAccount.curve25519Key, dehydratedDeviceAccount.oneTimeKeys.values.first()
        ).use { olmSession ->
            olmSession.encrypt(
                json.encodeToString(
                    decryptedOlmEventSerializer,
                    DecryptedOlmEvent(
                        content = megolmSession,
                        sender = bob,
                        senderKeys = keysOf(
                            Key.of(null, bobAccount.ed25519Key)
                        ),
                        recipient = alice,
                        recipientKeys = keysOf(
                            Key.of(null, dehydratedDeviceAccount.ed25519Key)
                        ),
                    )
                )
            )
        }
        val encryptedMegolmSession =
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(
                    dehydratedDeviceAccount.curve25519Key.base64 to CiphertextInfo.of(encryptedMessage)
                ), senderKey = KeyValue.of(bobAccount.curve25519Key)
            )

        keyStore.updateDeviceKeys(bob) {
            mapOf(
                "BOB_DEVICE" to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(
                            bob, "BOB_DEVICE", setOf(),
                            keysOf(
                                Key.of("BOB_DEVICE", bobAccount.ed25519Key),
                                Key.of("BOB_DEVICE", bobAccount.curve25519Key),
                            )
                        ), mapOf()
                    ),
                    Valid(false)
                )
            )
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice) {
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

    @Test
    fun `dehydrateDevice should create a new dehydrated device`() = runTest {
        val dehydratedDeviceKey = SecureRandom.nextBytes(32)
        var setDehydratedDevice: SetDehydratedDevice.Request? = null

        val (_, selfSigningPrivateKey) = driver.key.ed25519SecretKey().use {
            it.publicKey to it.base64
        }

        apiConfig.endpoints {
            matrixJsonEndpoint(SetDehydratedDevice) {
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
        setDehydratedDevice.shouldNotBeNull()

        when (driver) {
            is LibOlmCryptoDriver -> {
                val deviceData =
                    setDehydratedDevice.deviceData.shouldBeInstanceOf<DehydratedDeviceData.DehydrationV2Compatibility>()
                val dehydratedDeviceAccount = driver.olm.account.fromPickle(
                    decryptAesHmacSha2(
                        content = with(deviceData) {
                            AesHmacSha2EncryptedData(
                                iv = iv,
                                ciphertext = encryptedDevicePickle,
                                mac = mac
                            )
                        },
                        key = dehydratedDeviceKey,
                        name = DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
                    ).decodeToString(),
                )

                setDehydratedDevice.deviceId shouldBe dehydratedDeviceAccount.curve25519Key.base64
                setDehydratedDevice.deviceKeys.shouldNotBeNull()
                setDehydratedDevice.oneTimeKeys.shouldNotBeNull()
                setDehydratedDevice.fallbackKeys.shouldNotBeNull()
            }

            is VodozemacCryptoDriver -> {
                val deviceData =
                    setDehydratedDevice.deviceData.shouldBeInstanceOf<DehydratedDeviceData.DehydrationV2>()
                // casting needed due to a compiler bug (covariance with value class)
                val pickleKey =
                    checkNotNull((driver.key.pickleKey as PickleKeyFactory)(dehydratedDeviceKey)) { "pickle key was null" }
                val dehydratedDeviceAccount = driver.olm.account.fromDehydratedDevice(
                    pickle = deviceData.devicePickle,
                    nonce = deviceData.nonce,
                    pickleKey = pickleKey
                )

                setDehydratedDevice.deviceId shouldBe dehydratedDeviceAccount.curve25519Key.base64
                setDehydratedDevice.deviceKeys.shouldNotBeNull()
                setDehydratedDevice.oneTimeKeys.shouldNotBeNull()
                setDehydratedDevice.fallbackKeys.shouldNotBeNull()
            }
        }
    }
}
