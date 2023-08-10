package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class OutgoingSecretKeyRequestEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    val keyBackup = KeyBackupServiceMock()
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)


    lateinit var cut: OutgoingSecretKeyRequestEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutgoingSecretKeyRequestEventHandler(
            UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            OlmDecrypterMock(),
            keyBackup,
            keyStore,
            globalAccountDataStore,
            CurrentSyncState(currentSyncState)
        )
    }

    afterTest {
        scope.cancel()
    }

    val encryptedEvent = Event.ToDeviceEvent(
        EncryptedEventContent.OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = Key.Curve25519Key(null, "")
        ), bob
    )
    context(OutgoingSecretKeyRequestEventHandler::handleOutgoingKeyRequestAnswer.name) {
        val (crossSigningPublicKey, crossSigningPrivateKey) = freeAfter(OlmPkSigning.create(null)) { it.publicKey to it.privateKey }
        val (keyBackupPublicKey, keyBackupPrivateKey) = freeAfter(OlmPkDecryption.create(null)) { it.publicKey to it.privateKey }
        val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")
        suspend fun setDeviceKeys(trusted: Boolean) {
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                        KeySignatureTrustLevel.CrossSigned(trusted)
                    )
                )
            }
        }

        suspend fun setRequest(secretType: SecretType, receiverDeviceIds: Set<String>) {
            keyStore.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        secretType.id,
                        KeyRequestAction.REQUEST,
                        "OWN_ALICE_DEVICE",
                        "requestId"
                    ), receiverDeviceIds, Clock.System.now()
                )
            )
            keyStore.allSecretKeyRequests.first { it.size == 1 }
        }

        suspend fun setCrossSigningKeys(publicKey: String) {
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

        fun returnRoomKeysVersion(publicKey: String? = null) {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                    if (publicKey == null) throw MatrixServerException(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse.Unknown("")
                    )
                    else GetRoomKeysBackupVersionResponse.V1(
                        authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                            publicKey = Key.Curve25519Key(null, publicKey)
                        ), 1, "etag", "1"
                    )
                }
            }
        }
        should("ignore, when request was never made") {
            setDeviceKeys(true)
            setCrossSigningKeys(crossSigningPublicKey)
            val secretEvent = Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
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
        should("ignore, when sender device id cannot be found") {
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
        should("ignore when sender was not requested") {
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
        should("ignore when sender is not trusted") {
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
        should("ignore when public key of cross signing secret cannot be generated") {
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
        should("ignore when public key of key backup secret cannot be retrieved") {
            setDeviceKeys(true)
            setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(null)
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            globalAccountDataStore.save(Event.GlobalAccountDataEvent(secretEventContent))
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
        should("ignore when public key of cross signing secret does not match") {
            setDeviceKeys(true)
            setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(freeAfter(OlmPkSigning.create(null)) { it.publicKey })
            val secretEventContent = UserSigningKeyEventContent(mapOf())
            globalAccountDataStore.save(Event.GlobalAccountDataEvent(secretEventContent))
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
        should("ignore when public key of key backup secret does not match") {
            keyBackup.returnKeyBackupCanBeTrusted = false
            setDeviceKeys(true)
            setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(freeAfter(OlmPkDecryption.create(null)) { it.publicKey })
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            globalAccountDataStore.save(Event.GlobalAccountDataEvent(secretEventContent))
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
        should("ignore when encrypted secret could not be found") {
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
        should("save cross signing secret") {
            setDeviceKeys(true)
            setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(crossSigningPublicKey)
            val secretEvent = Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
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
        should("save cross key backup secret") {
            keyBackup.returnKeyBackupCanBeTrusted = true
            setDeviceKeys(true)
            setRequest(SecretType.M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(keyBackupPublicKey)
            val secretEvent = Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf()))
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
        should("cancel other requests") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.secret.request", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            setDeviceKeys(true)
            setRequest(SecretType.M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice, "OTHER_DEVICE"))
            setCrossSigningKeys(crossSigningPublicKey)
            val secretEvent = Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
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
    }
    context(OutgoingSecretKeyRequestEventHandler::cancelOldOutgoingKeyRequests.name) {
        should("only remove old requests and send cancel") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.secret.request", "txn"),
                    skipUrlCheck = true
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
                ), setOf(), Clock.System.now()
            )
            val request2 = StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId2"
                ), setOf(aliceDevice), (Clock.System.now() - 1.days)
            )
            keyStore.addSecretKeyRequest(request1)
            keyStore.addSecretKeyRequest(request2)
            keyStore.allSecretKeyRequests.first { it.size == 2 }

            cut.cancelOldOutgoingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))

            keyStore.allSecretKeyRequests.first { it.size == 1 } shouldBe setOf(request1)
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe SecretKeyRequestEventContent(
                SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                KeyRequestAction.REQUEST_CANCELLATION,
                "OWN_ALICE_DEVICE",
                "requestId2"
            )
        }
    }
    context(OutgoingSecretKeyRequestEventHandler::requestSecretKeys.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.secret.request", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
        }
        should("ignore when there are no missing secrets") {
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                        "key1"
                    ),
                    SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                        "key2"
                    ),
                    SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(
                        Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                        "key3"
                    )
                )
            }
            cut.requestSecretKeys()
            sendToDeviceEvents shouldBe null
        }
        should("send requests to verified cross signed devices") {
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(
                        Event.GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                        "key3"
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
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            keyStore.allSecretKeyRequests.first { it.size == 1 }
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    "DEVICE_1" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_1", setOf(), keysOf()), mapOf()),
                        KeySignatureTrustLevel.CrossSigned(false)
                    ),
                    "DEVICE_2" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_2", setOf(), keysOf()), mapOf()),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
            cut.requestSecretKeys()

            assertSoftly(sendToDeviceEvents?.get(alice)?.get("DEVICE_2")) {
                assertNotNull(this)
                this.shouldBeInstanceOf<SecretKeyRequestEventContent>()
                this.name shouldBe SecretType.M_CROSS_SIGNING_USER_SIGNING.id
                this.action shouldBe KeyRequestAction.REQUEST
                this.requestingDeviceId shouldBe aliceDevice
                this.requestId shouldNot beEmpty()
            }
            keyStore.allSecretKeyRequests.first { it.size == 2 } shouldHaveSize 2
        }
    }
    context(OutgoingSecretKeyRequestEventHandler::requestSecretKeysWhenCrossSigned.name) {
        should("request secret keys, when cross signed and verified") {
            currentSyncState.value = SyncState.RUNNING

            val sendToDeviceCalled = MutableStateFlow(false)
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                    sendToDeviceCalled.value = true
                }
            }

            val job = launch(start = CoroutineStart.UNDISPATCHED) {
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
            job.cancel()
        }
    }
    context(OutgoingSecretKeyRequestEventHandler::handleChangedSecrets.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.secret.request", "txn"),
                    skipUrlCheck = true
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
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            keyStore.allSecretKeyRequests.first { it.size == 1 }
        }
        should("do nothing when secret is not allowed to cache") {
            val crossSigningPrivateKeys = mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "key"
                )
            )
            keyStore.updateSecrets { crossSigningPrivateKeys }
            cut.handleChangedSecrets(Event.GlobalAccountDataEvent(MasterKeyEventContent(mapOf())))
            sendToDeviceEvents shouldBe null
            keyStore.getSecrets() shouldBe crossSigningPrivateKeys
        }
        should("do nothing when event did not change") {
            val event = Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
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
        should("remove cached secret and cancel ongoing requests when event did change") {
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("oh" to JsonPrimitive("change!")))),
                        "bla"
                    )
                )
            }
            cut.handleChangedSecrets(Event.GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())))

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
    }
}