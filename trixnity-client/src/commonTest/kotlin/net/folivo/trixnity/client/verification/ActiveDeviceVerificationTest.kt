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
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
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
    lateinit var api: MatrixClientServerApiClientImpl
    val json = createMatrixEventJson()
    lateinit var olmDecrypterMock: OlmDecrypterMock
    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock
    lateinit var keyStore: KeyStore

    lateinit var scope: CoroutineScope

    lateinit var cut: ActiveDeviceVerificationImpl

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
        cut = ActiveDeviceVerificationImpl(
            request = VerificationRequestToDeviceEventContent(
                bobDevice,
                setOf(Sas),
                timestamp.toEpochMilliseconds(),
                "t"
            ),
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
            matrixJsonEndpoint(Sync()) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(cancelEvent, bob)))
                )
            }
        }
        createCut()
        cut.startLifecycle(this)
        api.sync.startOnce(
            getBatchToken = { null },
            setBatchToken = {},
        ).getOrThrow()
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("handle cancel verification step before theirDeviceId is known") {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.key.verification.cancel", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        cut = ActiveDeviceVerificationImpl(
            request = VerificationRequestToDeviceEventContent(
                bobDevice,
                setOf(Sas),
                Clock.System.now().toEpochMilliseconds(),
                "t"
            ),
            requestIsOurs = false,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = null, // <-
            theirDeviceIds = setOf("bob1", "bob2"),
            supportedMethods = setOf(Sas),
            api = api,
            olmDecrypter = olmDecrypterMock,
            olmEncryptionService = olmEncryptionServiceMock,
            keyTrust = KeyTrustServiceMock(),
            keyStore = keyStore,
        )
        cut.startLifecycle(this)
        cut.cancel()
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(
            VerificationCancelEventContent(User, "user cancelled verification", null, "t"),
            true
        )
        sendToDeviceEvents shouldBe mapOf(
            bob to mapOf(
                "bob1" to VerificationCancelEventContent(
                    User, "user cancelled verification", null, "t"
                ),
                "bob2" to VerificationCancelEventContent(
                    User, "user cancelled verification", null, "t"
                ),
            )
        )
    }
    should("handle encrypted verification step") {
        createCut()
        cut.startLifecycle(this)
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        mapOf(),
                        Curve25519Key(null, "")
                    ), bob
                ),
                DecryptedOlmEvent(cancelEvent, bob, keysOf(), alice, keysOf())
            )
        )
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("send verification step and encrypt it") {
        val encrypted = OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519Key(null, "key")
        )
        olmEncryptionServiceMock.returnEncryptOlm = Result.success(encrypted)

        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.room.encrypted", "*"),
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
                SendToDevice("m.key.verification.cancel", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        olmEncryptionServiceMock.returnEncryptOlm =
            Result.failure(EncryptOlmError.OlmLibraryError(OlmLibraryException(message = "hu")))
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
            matrixJsonEndpoint(Sync()) {
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
        api.sync.startOnce(
            getBatchToken = { null },
            setBatchToken = {},
        ).getOrThrow()
    }
    should("stop lifecycle, when timed out") {
        val encrypted = OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519Key(null, "key")
        )
        olmEncryptionServiceMock.returnEncryptOlm = Result.success(encrypted)
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.room.encrypted", "*"),
            ) { }
        }
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("cancel request from other devices") {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        val readyEvent = VerificationReadyEventContent("ALICE_1", setOf(Sas), null, "t")
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync()) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(readyEvent, alice)))
                )
            }
            matrixJsonEndpoint(
                SendToDevice("m.key.verification.cancel", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
            matrixJsonEndpoint(
                SendToDevice("m.key.verification.cancel", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        olmEncryptionServiceMock.returnEncryptOlm =
            Result.failure(EncryptOlmError.OlmLibraryError(OlmLibraryException(message = "hu")))
        cut = ActiveDeviceVerificationImpl(
            request = VerificationRequestToDeviceEventContent(
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
        api.sync.startOnce(
            getBatchToken = { null },
            setBatchToken = {},
        ).getOrThrow()
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