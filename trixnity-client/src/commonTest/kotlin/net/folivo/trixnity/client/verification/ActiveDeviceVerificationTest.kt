package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.minutes

class ActiveDeviceVerificationTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"

    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var api: MatrixClientServerApiClient
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var olmDecrypterMock: OlmDecrypterMock
    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock
    lateinit var keyStore: KeyStore

    lateinit var scope: CoroutineScope

    lateinit var cut: ActiveDeviceVerification

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        api = newApi
        olmDecrypterMock = OlmDecrypterMock()
        olmEncryptionServiceMock = OlmEncryptionServiceMock()
        keyStore = getInMemoryKeyStore(scope)
    }

    afterTest {
        scope.cancel()
    }

    fun createCut(timestamp: Instant = Clock.System.now()) {
        cut = ActiveDeviceVerification(
            request = VerificationRequestEventContent(bobDevice, setOf(Sas), timestamp.toEpochMilliseconds(), "t"),
            requestIsOurs = false,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            supportedMethods = setOf(Sas),
            api = api,
            olmDecrypter = olmDecrypterMock,
            olmEncryptionService = olmEncryptionServiceMock,
            keyTrust = KeyTrustServiceMock(),
            keyStore = keyStore,
        )
    }

    should("handle verification step") {
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        apiConfig.endpoints {
            matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(cancelEvent, bob)))
                )
            }
        }
        createCut()
        cut.startLifecycle(this)
        api.sync.startOnce().getOrThrow()
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("handle encrypted verification step") {
        createCut()
        cut.startLifecycle(this)
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bob),
                DecryptedOlmEvent(cancelEvent, bob, keysOf(), alice, keysOf())
            )
        )
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("send verification step and encrypt it") {
        val encrypted = OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519Key(null, "key")
        )
        olmEncryptionServiceMock.returnEncryptOlm = { encrypted }

        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                json, mappings,
                SendToDevice("m.room.encrypted", "txn"),
                skipUrlCheck = true
            ) {
                sendToDeviceEvents = it.messages
            }
        }

        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        olmEncryptionServiceMock.encryptOlmCalled shouldNotBe null
        cut.state.first { it is ActiveVerificationState.Cancel }
        sendToDeviceEvents shouldBe mapOf(bob to mapOf(bobDevice to encrypted))
    }
    should("send verification step and use unencrypted when encrypt failed") {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                json, mappings,
                SendToDevice("m.key.verification.cancel", "txn"),
                skipUrlCheck = true
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        olmEncryptionServiceMock.returnEncryptOlm = { throw OlmLibraryException(message = "hu") }
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        sendToDeviceEvents shouldBe mapOf(
            bob to mapOf(
                bobDevice to VerificationCancelEventContent(User, "user cancelled verification", null, "t")
            )
        )
    }
    should("stop lifecycle, when cancelled") {
        apiConfig.endpoints {
            matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(
                        listOf(
                            ToDeviceEvent(
                                VerificationCancelEventContent(User, "u", null, "t"), bob
                            )
                        )
                    )
                )
            }
        }
        createCut()
        cut.startLifecycle(this)
        api.sync.startOnce().getOrThrow()
    }
    should("stop lifecycle, when timed out") {
        val encrypted = OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519Key(null, "key")
        )
        olmEncryptionServiceMock.returnEncryptOlm = { encrypted }
        apiConfig.endpoints {
            matrixJsonEndpoint(
                json, mappings,
                SendToDevice("m.room.encrypted", "txn"),
                skipUrlCheck = true
            ) { }
        }
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("cancel request from other devices") {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        val readyEvent = VerificationReadyEventContent("ALICE_1", setOf(Sas), null, "t")
        apiConfig.endpoints {
            matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(readyEvent, alice)))
                )
            }
            matrixJsonEndpoint(
                json, mappings,
                SendToDevice("m.key.verification.cancel", "txn"),
                skipUrlCheck = true
            ) {
                sendToDeviceEvents = it.messages
            }
            matrixJsonEndpoint(
                json, mappings,
                SendToDevice("m.key.verification.cancel", "txn"),
                skipUrlCheck = true
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        olmEncryptionServiceMock.returnEncryptOlm = { throw OlmLibraryException(message = "hu") }
        cut = ActiveDeviceVerification(
            request = VerificationRequestEventContent(
                aliceDevice,
                setOf(Sas),
                Clock.System.now().toEpochMilliseconds(),
                "t"
            ),
            requestIsOurs = false,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = alice,
            theirDeviceId = null,
            theirDeviceIds = setOf("ALICE_1", "ALICE_2"),
            supportedMethods = setOf(Sas),
            api = api,
            olmDecrypter = olmDecrypterMock,
            olmEncryptionService = olmEncryptionServiceMock,
            keyTrust = KeyTrustServiceMock(),
            keyStore = keyStore,
        )
        cut.startLifecycle(this)
        api.sync.startOnce().getOrThrow()
        cut.state.first { it is ActiveVerificationState.Ready }

        cut.theirDeviceId shouldBe "ALICE_1"
        sendToDeviceEvents shouldBe mapOf(
            alice to mapOf(
                "ALICE_2" to VerificationCancelEventContent(
                    Accepted, "accepted by other device", null, "t"
                )
            )
        )
        cut.cancel()
        sendToDeviceEvents shouldBe mapOf(
            alice to mapOf(
                "ALICE_1" to VerificationCancelEventContent(
                    User, "user cancelled verification", null, "t"
                )
            )
        )
    }
})