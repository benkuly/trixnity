package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeyBackupVersion
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class KeySecretServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    lateinit var olmEvent: OlmEventServiceMock
    val keyBackup = KeyBackupServiceMock()
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)


    lateinit var cut: KeySecretService

    beforeTest {
        olmEvent = OlmEventServiceMock()
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeySecretService(alice, aliceDevice, store, olmEvent, keyBackup, api, currentSyncState)
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

    context(KeySecretService::handleEncryptedIncomingKeyRequests.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            store.account.userId.value = alice
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), null),
                        Valid(true)
                    )
                )
            }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.room.encrypted", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            store.keys.secrets.value =
                mapOf(
                    M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                        "secretUserSigningKey"
                    )
                )
            olmEvent.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
            }
        }
        should("ignore request from other user") {
            cut.handleEncryptedIncomingKeyRequests(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            bobDevice,
                            "requestId"
                        ),
                        bob, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents shouldBe null
        }
        should("add request on request") {
            cut.handleEncryptedIncomingKeyRequests(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
        }
        should("remove request on request cancellation") {
            cut.handleEncryptedIncomingKeyRequests(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.handleEncryptedIncomingKeyRequests(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST_CANCELLATION,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents shouldBe null
        }
    }
    context(KeySecretService::processIncomingKeyRequests.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            store.account.userId.value = alice
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.room.encrypted", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            store.keys.secrets.value =
                mapOf(
                    M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                        "secretUserSigningKey"
                    )
                )
            olmEvent.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
            }
        }
        suspend fun ShouldSpecContainerScope.answerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("answer request with trust level $returnedTrustLevel") {
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            returnedTrustLevel
                        )
                    )
                }
                cut.handleEncryptedIncomingKeyRequests(
                    IOlmService.DecryptedOlmEventContainer(
                        encryptedEvent, DecryptedOlmEvent(
                            SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
                sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
            }
        }
        answerRequest(Valid(true))
        answerRequest(CrossSigned(true))
        suspend fun ShouldSpecContainerScope.notAnswerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("not answer request with trust level $returnedTrustLevel") {
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            returnedTrustLevel
                        )
                    )
                }
                cut.handleEncryptedIncomingKeyRequests(
                    IOlmService.DecryptedOlmEventContainer(
                        encryptedEvent, DecryptedOlmEvent(
                            SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
                sendToDeviceEvents shouldBe null
            }
        }
        notAnswerRequest(Valid(false))
        notAnswerRequest(CrossSigned(false))
        notAnswerRequest(NotCrossSigned)
        notAnswerRequest(Blocked)
        notAnswerRequest(Invalid("reason"))
    }
    context(KeySecretService::handleOutgoingKeyRequestAnswer.name) {
        val (crossSigningPublicKey, crossSigningPrivateKey) = freeAfter(OlmPkSigning.create(null)) { it.publicKey to it.privateKey }
        val (keyBackupPublicKey, keyBackupPrivateKey) = freeAfter(OlmPkDecryption.create(null)) { it.publicKey to it.privateKey }
        val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")
        suspend fun setDeviceKeys(trusted: Boolean) {
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                        CrossSigned(trusted)
                    )
                )
            }
        }

        suspend fun setRequest(secretType: AllowedSecretType, receiverDeviceIds: Set<String>) {
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        secretType.id,
                        KeyRequestAction.REQUEST,
                        "OWN_ALICE_DEVICE",
                        "requestId"
                    ), receiverDeviceIds, Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
        }

        suspend fun setCrossSigningKeys(publicKey: String) {
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.UserSigningKey), keysOf(
                                    Key.Ed25519Key(publicKey, publicKey)
                                )
                            ), mapOf()
                        ), CrossSigned(true)
                    )
                )
            }
        }

        fun returnRoomKeysVersion(publicKey: String? = null) {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, mappings, GetRoomKeyBackupVersion()) {
                    if (publicKey == null) throw MatrixServerException(InternalServerError, ErrorResponse.Unknown(""))
                    else GetRoomKeysBackupVersionResponse.V1(
                        authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                            publicKey = Key.Curve25519Key(null, publicKey)
                        ), 1, "etag", "1"
                    )
                }
            }
        }
        should("ignore, when sender device id cannot be found") {
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when sender was not requested") {
            setDeviceKeys(true)
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf("OTHER_DEVICE"))
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when sender is not trusted") {
            setDeviceKeys(false)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key of cross signing secret cannot be generated") {
            setDeviceKeys(true)
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(crossSigningPublicKey)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", "dino"),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key of key backup secret cannot be retrieved") {
            setDeviceKeys(true)
            setRequest(M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(null)
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key of cross signing secret does not match") {
            setDeviceKeys(true)
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(freeAfter(OlmPkSigning.create(null)) { it.publicKey })
            val secretEventContent = UserSigningKeyEventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key of key backup secret does not match") {
            keyBackup.returnKeyBackupCanBeTrusted = false
            setDeviceKeys(true)
            setRequest(M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(freeAfter(OlmPkDecryption.create(null)) { it.publicKey })
            val secretEventContent = MegolmBackupV1EventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when encrypted secret could not be found") {
            setDeviceKeys(true)
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(crossSigningPublicKey)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("save cross signing secret") {
            setDeviceKeys(true)
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice))
            setCrossSigningKeys(crossSigningPublicKey)
            val secretEvent = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            store.globalAccountData.update(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            store.keys.secrets.first { it.size == 1 } shouldBe mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, crossSigningPrivateKey)
            )
        }
        should("save cross key backup secret") {
            keyBackup.returnKeyBackupCanBeTrusted = true
            setDeviceKeys(true)
            setRequest(M_MEGOLM_BACKUP_V1, setOf(aliceDevice))
            returnRoomKeysVersion(keyBackupPublicKey)
            val secretEvent = GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf()))
            store.globalAccountData.update(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", keyBackupPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            store.keys.secrets.first { it.size == 1 } shouldBe mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(secretEvent, keyBackupPrivateKey)
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
            setRequest(M_CROSS_SIGNING_USER_SIGNING, setOf(aliceDevice, "OTHER_DEVICE"))
            setCrossSigningKeys(crossSigningPublicKey)
            val secretEvent = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            store.globalAccountData.update(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                IOlmService.DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeySendEventContent("requestId", crossSigningPrivateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            store.keys.secrets.first { it.size == 1 } shouldBe mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, crossSigningPrivateKey)
            )
            sendToDeviceEvents?.get(alice)?.get("OTHER_DEVICE") shouldBe SecretKeyRequestEventContent(
                M_CROSS_SIGNING_USER_SIGNING.id,
                KeyRequestAction.REQUEST_CANCELLATION,
                "OWN_ALICE_DEVICE",
                "requestId"
            )
        }
    }
    context(KeySecretService::cancelOldOutgoingKeyRequests.name) {
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
                    M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId1"
                ), setOf(), Clock.System.now()
            )
            val request2 = StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId2"
                ), setOf(aliceDevice), (Clock.System.now() - 1.days)
            )
            store.keys.addSecretKeyRequest(request1)
            store.keys.addSecretKeyRequest(request2)
            store.keys.allSecretKeyRequests.first { it.size == 2 }

            cut.cancelOldOutgoingKeyRequests()

            store.keys.allSecretKeyRequests.first { it.size == 1 } shouldBe setOf(request1)
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe SecretKeyRequestEventContent(
                M_CROSS_SIGNING_USER_SIGNING.id,
                KeyRequestAction.REQUEST_CANCELLATION,
                "OWN_ALICE_DEVICE",
                "requestId2"
            )
        }
    }
    context(KeySecretService::requestSecretKeys.name) {
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
            store.keys.secrets.value = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "key1"
                ),
                M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                    "key2"
                ),
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key3"
                )
            )
            cut.requestSecretKeys()
            sendToDeviceEvents shouldBe null
        }
        should("send requests to verified cross signed devices") {
            store.keys.secrets.value = mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(MegolmBackupV1EventContent(mapOf())),
                    "key3"
                )
            )
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        M_CROSS_SIGNING_SELF_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId1"
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    "DEVICE_1" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_1", setOf(), keysOf()), mapOf()),
                        CrossSigned(false)
                    ),
                    "DEVICE_2" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_2", setOf(), keysOf()), mapOf()),
                        CrossSigned(true)
                    )
                )
            }
            cut.requestSecretKeys()

            assertSoftly(sendToDeviceEvents?.get(alice)?.get("DEVICE_2")) {
                assertNotNull(this)
                this.shouldBeInstanceOf<SecretKeyRequestEventContent>()
                this.name shouldBe M_CROSS_SIGNING_USER_SIGNING.id
                this.action shouldBe KeyRequestAction.REQUEST
                this.requestingDeviceId shouldBe aliceDevice
                this.requestId shouldNot beEmpty()
            }
            store.keys.allSecretKeyRequests.first { it.size == 2 } shouldHaveSize 2
        }
    }
    context(KeySecretService::requestSecretKeysWhenCrossSigned.name) {
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
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                        CrossSigned(true)
                    ),
                    "OTHER_ALICE" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "OTHER_ALICE", setOf(), keysOf()), mapOf()),
                        CrossSigned(true)
                    ),
                )
            }
            sendToDeviceCalled.first { it }
            job.cancel()
        }
    }
    context(KeySecretService::handleChangedSecrets.name) {
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
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId1"
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
        }
        should("do nothing when secret is not allowed to cache") {
            val crossSigningPrivateKeys = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "key"
                )
            )
            store.keys.secrets.value = crossSigningPrivateKeys
            cut.handleChangedSecrets(GlobalAccountDataEvent(MasterKeyEventContent(mapOf())))
            sendToDeviceEvents shouldBe null
            store.keys.secrets.value shouldBe crossSigningPrivateKeys
        }
        should("do nothing when event did not change") {
            val event = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            val crossSigningPrivateKeys = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    event, "bla"
                )
            )
            store.keys.secrets.value = crossSigningPrivateKeys
            cut.handleChangedSecrets(event)
            sendToDeviceEvents shouldBe null
            store.keys.secrets.value shouldBe crossSigningPrivateKeys
        }
        should("remove cached secret and cancel ongoing requests when event did change") {
            store.keys.secrets.value = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf("oh" to JsonPrimitive("change!")))),
                    "bla"
                )
            )
            cut.handleChangedSecrets(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())))

            assertSoftly(sendToDeviceEvents?.get(alice)?.get("DEVICE_2")) {
                assertNotNull(this)
                this.shouldBeInstanceOf<SecretKeyRequestEventContent>()
                this.name shouldBe M_CROSS_SIGNING_USER_SIGNING.id
                this.action shouldBe KeyRequestAction.REQUEST_CANCELLATION
                this.requestingDeviceId shouldBe aliceDevice
                this.requestId shouldBe "requestId1"
            }
            store.keys.secrets.value shouldBe mapOf()
        }
    }
    context(KeySecretService::decryptMissingSecrets.name) {
        should("decrypt missing secrets and update secure store") {
            val existingPrivateKeys = mapOf(
                M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key2"
                ),
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key3"
                )
            )
            store.keys.secrets.value = existingPrivateKeys

            val key = Random.nextBytes(32)
            val secret = Random.nextBytes(32).encodeBase64()
            val encryptedData = encryptAesHmacSha2(
                content = secret.encodeToByteArray(),
                key = key,
                name = "m.cross_signing.user_signing"
            )

            val event = GlobalAccountDataEvent(
                UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
            )
            store.globalAccountData.update(event)

            cut.decryptMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
            store.keys.secrets.value shouldBe existingPrivateKeys + mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(event, secret),
            )
        }
    }
}