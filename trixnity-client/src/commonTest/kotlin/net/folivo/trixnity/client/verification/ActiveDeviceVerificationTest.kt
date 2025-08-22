package net.folivo.trixnity.client.verification

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
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
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmEncryptionService.EncryptOlmError
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveDeviceVerificationTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BBBBBB"

    private val json = createMatrixEventJson()

    private val olmDecrypterMock = OlmDecrypterMock()
    private val olmEncryptionServiceMock = OlmEncryptionServiceMock()

    private val keyStore = getInMemoryKeyStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(
        config = apiConfig,
        json = json
    )

    @Test
    fun `handle verification step`() = runTest {
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        apiConfig.endpoints {
            matrixJsonEndpoint(Sync()) {
                Sync.Response(
                    nextBatch = "nextBatch",
                    toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(cancelEvent, bob)))
                )
            }
        }
        val cut = createCut()
        cut.startLifecycle(this)
        api.sync.startOnce().getOrThrow()
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }

    @Test
    fun `handle cancel verification step before theirDeviceId is known`() = runTest {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.key.verification.cancel", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        val cut = ActiveDeviceVerificationImpl(
            request = VerificationRequestToDeviceEventContent(
                bobDevice,
                setOf(Sas),
                currentTime,
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
            clock = testClock,
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

    @Test
    fun `handle encrypted verification step`() = runTest {
        val cut = createCut()
        cut.startLifecycle(this)
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        olmDecrypterMock.eventSubscribers.first().first()(
            DecryptedOlmEventContainer(
                ToDeviceEvent(
                    OlmEncryptedToDeviceEventContent(
                        mapOf(),
                        Curve25519KeyValue("")
                    ), bob
                ),
                DecryptedOlmEvent(cancelEvent, bob, keysOf(), alice, keysOf())
            )
        )
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }

    @Test
    fun `send verification step and encrypt it`() = runTest {
        val encrypted = OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519KeyValue("key")
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

        val cut = createCut()
        cut.startLifecycle(this)
        cut.cancel()
        olmEncryptionServiceMock.encryptOlmCalled shouldNotBe null
        cut.state.first { it is ActiveVerificationState.Cancel }
        sendToDeviceEvents shouldBe mapOf(bob to mapOf(bobDevice to encrypted))
    }

    @Test
    fun `send verification step and use unencrypted when encrypt failed`() = runTest {
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
        val cut = createCut()
        cut.startLifecycle(this)
        cut.cancel()
        sendToDeviceEvents shouldBe mapOf(
            bob to mapOf(
                bobDevice to VerificationCancelEventContent(User, "user cancelled verification", null, "t")
            )
        )
    }

    @Test
    fun `stop lifecycle when cancelled`() = runTest {
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
        val cut = createCut()
        cut.startLifecycle(this)
        api.sync.startOnce().getOrThrow()
    }

    @Test
    fun `stop lifecycle when timed out`() = runTest {
        val encrypted = OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519KeyValue("key")
        )
        olmEncryptionServiceMock.returnEncryptOlm = Result.success(encrypted)
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.room.encrypted", "*"),
            ) { }
        }
        val cut = createCut(testClock.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }

    @Test
    fun `cancel request from other devices`() = runTest {
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
        val cut = ActiveDeviceVerificationImpl(
            request = VerificationRequestToDeviceEventContent(
                aliceDevice,
                setOf(Sas),
                currentTime,
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
            clock = testClock,
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

    private fun TestScope.createCut(timestamp: Instant = testClock.now()): ActiveDeviceVerificationImpl =
        ActiveDeviceVerificationImpl(
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
            clock = testClock,
        )
}