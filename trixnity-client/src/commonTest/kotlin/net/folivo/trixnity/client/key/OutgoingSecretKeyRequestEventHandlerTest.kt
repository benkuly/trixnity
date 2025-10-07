package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.devices.DehydratedDeviceData
import net.folivo.trixnity.clientserverapi.model.devices.GetDehydratedDevice
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DehydratedDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.test.utils.*
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(MSC3814::class)
class OutgoingSecretKeyRequestEventHandlerTest : TrixnityBaseTest() {
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val keyStore = getInMemoryKeyStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val keyBackup = KeyBackupServiceMock()
    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = OutgoingSecretKeyRequestEventHandler(
        UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api,
        OlmDecrypterMock(),
        keyBackup,
        keyStore,
        globalAccountDataStore,
        CurrentSyncState(currentSyncState),
        testScope.testClock,
    )

    private val encryptedEvent = ToDeviceEvent(
        OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519KeyValue("")
        ), bob
    )

    private val _crossSigningKeys by suspendLazy { freeAfter(OlmPkSigning.create(null)) { it.publicKey to it.privateKey } }
    private val crossSigningPublicKey by suspendLazy { _crossSigningKeys.first }
    private val crossSigningPrivateKey by suspendLazy { _crossSigningKeys.second }

    private val _backupKeys by suspendLazy { freeAfter(OlmPkDecryption.create(null)) { it.publicKey to it.privateKey } }
    private val keyBackupPublicKey by suspendLazy { _backupKeys.first }
    private val keyBackupPrivateKey by suspendLazy { _backupKeys.second }

    private val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when request was never made`() = runTest {
        setDeviceKeys(true)
        setCrossSigningKeys(crossSigningPublicKey)
        val secretEvent = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
        globalAccountDataStore.save(secretEvent)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        continually(500.milliseconds) {
            keyStore.getSecrets() shouldBe mapOf()
        }
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when sender device id cannot be found`() = runTest {
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        continually(500.milliseconds) {
            keyStore.getSecrets() shouldBe mapOf()
        }
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when sender was not requested`() = runTest {
        setDeviceKeys(true)
        setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf("OTHER_DEVICE"))
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        continually(500.milliseconds) {
            keyStore.getSecrets() shouldBe mapOf()
        }
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when sender is not trusted`() = runTest {
        setDeviceKeys(false)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        continually(500.milliseconds) {
            keyStore.getSecrets() shouldBe mapOf()
        }
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when public key of cross signing secret cannot be generated`() =
        runTest {
            setDeviceKeys(true)
            setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(crossSigningPublicKey)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", "dino"),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when public key of key backup secret cannot be retrieved`() =
        runTest {
            setDeviceKeys(true)
            setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(null)
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            globalAccountDataStore.save(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when public key of cross signing secret does not match`() =
        runTest {
            setDeviceKeys(true)
            setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(freeAfter(OlmPkSigning.create(null)) { it.publicKey })
            val secretEventContent = UserSigningKeyEventContent(mapOf())
            globalAccountDataStore.save(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when public key of key backup secret does not match`() =
        runTest {
            keyBackup.returnKeyBackupCanBeTrusted = false
            setDeviceKeys(true)
            setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(freeAfter(OlmPkDecryption.create(null)) { it.publicKey })
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            globalAccountDataStore.save(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when dehydrated device cannot be decrypted`() =
        runTest {
            val key = SecureRandom.nextBytes(32).encodeUnpaddedBase64()
            setDeviceKeys(true)
            setRequest(SecretType.M_DEHYDRATED_DEVICE, setOf(aliceDevice))
            returnDehydratedDevice(key)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", "wrong key"),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when dehydrated device cannot be unpickled`() =
        runTest {
            val key = SecureRandom.nextBytes(32).encodeUnpaddedBase64()
            setDeviceKeys(true)
            setRequest(SecretType.M_DEHYDRATED_DEVICE, setOf(aliceDevice))
            returnDehydratedDevice(key, "invalid pickle")
            val secretEvent = GlobalAccountDataEvent(DehydratedDeviceEventContent(mapOf()))
            globalAccountDataStore.save(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", key),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when dehydrated device cannot be retrieved`() =
        runTest {
            val key = SecureRandom.nextBytes(32).encodeUnpaddedBase64()
            setDeviceKeys(true)
            setRequest(SecretType.M_DEHYDRATED_DEVICE, setOf(aliceDevice))
            returnDehydratedDevice(null)
            val secretEvent = GlobalAccountDataEvent(DehydratedDeviceEventContent(mapOf()))
            globalAccountDataStore.save(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", key),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                keyStore.getSecrets() shouldBe mapOf()
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » ignore when encrypted secret could not be found`() = runTest {
        setDeviceKeys(true)
        setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
        setCrossSigningKeys(crossSigningPublicKey)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        continually(500.milliseconds) {
            keyStore.getSecrets() shouldBe mapOf()
        }
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » save cross signing secret`() = runTest {
        setDeviceKeys(true)
        setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
        setCrossSigningKeys(crossSigningPublicKey)
        val secretEvent = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
        globalAccountDataStore.save(secretEvent)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        keyStore.getSecretsFlow().first { it.size == 1 } shouldBe mapOf(
            SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, crossSigningPrivateKey)
        )
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » save cross key backup secret`() = runTest {
        keyBackup.returnKeyBackupCanBeTrusted = true
        setDeviceKeys(true)
        setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
        returnRoomKeysVersion(keyBackupPublicKey)
        val secretEvent = GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf()))
        globalAccountDataStore.save(secretEvent)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        keyStore.getSecretsFlow().first { it.size == 1 } shouldBe mapOf(
            SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(secretEvent, keyBackupPrivateKey)
        )
    }

    @Test
    fun `handleOutgoingKeyRequestAnswer » save dehydrated device secret`() =
        runTest {
            val key = SecureRandom.nextBytes(32).encodeUnpaddedBase64()
            setDeviceKeys(true)
            setRequest(SecretType.M_DEHYDRATED_DEVICE, setOf(aliceDevice))
            returnDehydratedDevice(key)
            val secretEvent = GlobalAccountDataEvent(DehydratedDeviceEventContent(mapOf()))
            globalAccountDataStore.save(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", key),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                SecretType.M_DEHYDRATED_DEVICE to StoredSecret(secretEvent, keyBackupPrivateKey)
            }
        }

    @Test
    fun `handleOutgoingKeyRequestAnswer » cancel other requests`() = runTest {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.secret.request", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        setDeviceKeys(true)
        setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice, "OTHER_DEVICE"))
        setCrossSigningKeys(crossSigningPublicKey)
        val secretEvent = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
        globalAccountDataStore.save(secretEvent)
        cut.handleOutgoingKeyRequestAnswer(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                    alice, keysOf(aliceDevice2Key), alice, keysOf()
                )
            )
        )
        keyStore.getSecretsFlow().first { it.size == 1 } shouldBe mapOf(
            SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, crossSigningPrivateKey)
        )
        sendToDeviceEvents?.get(alice)?.get("OTHER_DEVICE") shouldBe SecretKeyRequestEventContent(
            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
            KeyRequestAction.REQUEST_CANCELLATION,
            "OWN_ALICE_DEVICE",
            "requestId"
        )
    }

    @Test
    fun `cancelOldOutgoingKeyRequests » only remove old requests and send cancel`() = runTest {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.secret.request", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        val request1 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent(
                SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                KeyRequestAction.REQUEST,
                "OWN_ALICE_DEVICE",
                "requestId1"
            ), setOf(), testClock.now()
        )
        val request2 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent(
                SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                KeyRequestAction.REQUEST,
                "OWN_ALICE_DEVICE",
                "requestId2"
            ), setOf(aliceDevice), (testClock.now() - 1.days - 1.seconds)
        )
        keyStore.addSecretKeyRequest(request1)
        keyStore.addSecretKeyRequest(request2)
        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 2 }

        cut.cancelOldOutgoingKeyRequests()

        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 1 } shouldBe setOf(request1)
        sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe SecretKeyRequestEventContent(
            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
            KeyRequestAction.REQUEST_CANCELLATION,
            "OWN_ALICE_DEVICE",
            "requestId2"
        )
    }

    private fun requestSecretKeysSetup() {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.secret.request", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
    }


    @Test
    fun `requestSecretKeysSetup » ignore when there are no missing secrets`() = runTest {
        requestSecretKeysSetup()
        keyStore.updateSecrets {
            mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "key1"
                ),
                SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                    "key2"
                ),
                SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key3"
                ),
                @OptIn(MSC3814::class)
                SecretType.M_DEHYDRATED_DEVICE to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key4"
                )
            )
        }
        cut.requestSecretKeys()
        sendToDeviceEvents shouldBe null
    }

    @Test
    fun `requestSecretKeysSetup » send requests to verified cross signed devices only`() = runTest {
        requestSecretKeysSetup()
        keyStore.updateSecrets {
            mapOf(
                SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key3"
                ),
                @OptIn(MSC3814::class)
                SecretType.M_DEHYDRATED_DEVICE to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key4"
                )
            )
        }
        keyStore.addSecretKeyRequest(
            StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    SecretType.M_CROSS_SIGNING_SELF_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    aliceDevice,
                    "requestId1"
                ),
                setOf("DEVICE_2"),
                testClock.now(),
            )
        )
        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 1 }
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                "DEVICE_1" to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, "DEVICE_1", setOf(), keysOf()), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(false)
                ),
                "DEVICE_2" to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, "DEVICE_2", setOf(), keysOf()), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(true)
                ),
                "DEVICE_3" to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, "DEVICE_3", setOf(), keysOf(), dehydrated = true), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(true)
                )
            )
        }
        cut.requestSecretKeys()

        val toDeviceMessages = sendToDeviceEvents.shouldNotBeNull()
        toDeviceMessages.shouldHaveSize(1)
        val aliceToDeviceMessages = toDeviceMessages[alice].shouldNotBeNull()
        aliceToDeviceMessages.shouldHaveSize(1)
        assertSoftly(aliceToDeviceMessages["DEVICE_2"]) {
            assertNotNull(this)
            this.shouldBeInstanceOf<SecretKeyRequestEventContent>()
            this.name shouldBe SecretType.M_CROSS_SIGNING_USER_SIGNING.id
            this.action shouldBe KeyRequestAction.REQUEST
            this.requestingDeviceId shouldBe aliceDevice
            this.requestId shouldNot beEmpty()
        }
        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 2 } shouldHaveSize 2
    }

    @Test
    fun `requestSecretKeysWhenCrossSigned » request secret keys when cross signed and verified`() = runTest {
        currentSyncState.value = SyncState.RUNNING

        val sendToDeviceCalled = MutableStateFlow(false)
        apiConfig.endpoints {
            matrixJsonEndpoint(SendToDevice("m.secret.request", "*")) {
                sendToDeviceCalled.value = true
            }
        }

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cut.requestSecretKeysWhenCrossSigned()
        }
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(true)
                ),
                "OTHER_ALICE" to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, "OTHER_ALICE", setOf(), keysOf()), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(true)
                ),
            )
        }
        sendToDeviceCalled.first { it }
    }

    private var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null

    private suspend fun TestScope.handleChangedSecretsSetup() {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.secret.request", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        keyStore.addSecretKeyRequest(
            StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    aliceDevice,
                    "requestId1"
                ),
                setOf("DEVICE_2"),
                testClock.now(),
            )
        )
        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 1 }
    }

    @Test
    fun `handleChangedSecrets » do nothing when secret is not allowed to cache`() = runTest {
        handleChangedSecretsSetup()
        val crossSigningPrivateKeys = mapOf(
            SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                "key"
            )
        )
        keyStore.updateSecrets { crossSigningPrivateKeys }
        cut.handleChangedSecrets(GlobalAccountDataEvent(MasterKeyEventContent(mapOf())))
        sendToDeviceEvents shouldBe null
        keyStore.getSecrets() shouldBe crossSigningPrivateKeys
    }

    @Test
    fun `handleChangedSecrets » do nothing when event did not change`() = runTest {
        handleChangedSecretsSetup()
        val event = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
        val crossSigningPrivateKeys = mapOf(
            SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                event, "bla"
            )
        )
        keyStore.updateSecrets { crossSigningPrivateKeys }
        cut.handleChangedSecrets(event)
        sendToDeviceEvents shouldBe null
        keyStore.getSecrets() shouldBe crossSigningPrivateKeys
    }

    @Test
    fun `handleChangedSecrets » remove cached secret and cancel ongoing requests when event did change`() =
        runTest {
            handleChangedSecretsSetup()
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("oh" to JsonPrimitive("change!")))),
                        "bla"
                    )
                )
            }
            cut.handleChangedSecrets(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())))

            assertSoftly(sendToDeviceEvents?.get(alice)?.get("DEVICE_2")) {
                assertNotNull(this)
                this.shouldBeInstanceOf<SecretKeyRequestEventContent>()
                this.name shouldBe SecretType.M_CROSS_SIGNING_USER_SIGNING.id
                this.action shouldBe KeyRequestAction.REQUEST_CANCELLATION
                this.requestingDeviceId shouldBe aliceDevice
                this.requestId shouldBe "requestId1"
            }
            keyStore.getSecrets() shouldBe mapOf()
        }

    private suspend fun setDeviceKeys(trusted: Boolean) {
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(trusted)
                )
            )
        }
    }

    private suspend fun TestScope.setRequest(secretType: SecretType, receiverDeviceIds: Set<String>) {
        keyStore.addSecretKeyRequest(
            StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    secretType.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId"
                ),
                receiverDeviceIds,
                testClock.now(),
            )
        )
        keyStore.getAllSecretKeyRequestsFlow().first { it.size == 1 }
    }

    private suspend fun setCrossSigningKeys(publicKey: String) {
        keyStore.updateCrossSigningKeys(alice) {
            setOf(
                StoredCrossSigningKeys(
                    SignedCrossSigningKeys(
                        CrossSigningKeys(
                            alice, setOf(CrossSigningKeysUsage.UserSigningKey), keysOf(
                                Key.Ed25519Key(publicKey, publicKey)
                            )
                        ), mapOf()
                    ), KeySignatureTrustLevel.CrossSigned(true)
                )
            )
        }
    }

    private fun returnRoomKeysVersion(publicKey: String? = null) {
        apiConfig.endpoints {
            matrixJsonEndpoint(GetRoomKeyBackupVersion()) {
                if (publicKey == null) throw MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse.Unknown("")
                )
                else GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Curve25519KeyValue(publicKey)
                    ), 1, "etag", "1"
                )
            }
        }
    }

    private fun returnDehydratedDevice(key: String? = null, pickle: String? = null) {
        apiConfig.endpoints {
            matrixJsonEndpoint(GetDehydratedDevice()) {
                if (key == null) throw MatrixServerException(
                    HttpStatusCode.NotFound,
                    ErrorResponse.NotFound("no dehydrated device present")
                )
                else GetDehydratedDevice.Response(
                    deviceId = "dehydratedDeviceId",
                    deviceData =
                        with(
                            encryptAesHmacSha2(
                                (pickle ?: freeAfter(OlmAccount.create()) { it.pickle(null) }).encodeToByteArray(),
                                key.decodeUnpaddedBase64Bytes(),
                                DehydratedDeviceData.DehydrationV2Compatibility.ALGORITHM
                            )
                        ) {
                            DehydratedDeviceData.DehydrationV2Compatibility(
                                iv = iv,
                                encryptedDevicePickle = ciphertext,
                                mac = mac,
                            )
                        }
                )
            }
        }
    }
}