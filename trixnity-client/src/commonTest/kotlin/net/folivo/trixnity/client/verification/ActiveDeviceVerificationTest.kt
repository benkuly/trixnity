package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmService.DecryptedOlmEvent
import net.folivo.trixnity.core.EventSubscriber
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.OlmEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.olm.OlmLibraryException
import kotlin.time.Duration.Companion.minutes

class ActiveDeviceVerificationTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"

    val api = mockk<MatrixApiClient>(relaxed = true)
    val olm = mockk<OlmService>(relaxed = true)

    lateinit var cut: ActiveDeviceVerification

    lateinit var encryptedStepFlow: MutableSharedFlow<DecryptedOlmEvent>

    beforeTest {
        encryptedStepFlow = MutableSharedFlow()
        coEvery { api.json } returns mockk()
        coEvery { olm.decryptedOlmEvents } returns encryptedStepFlow
    }
    afterTest {
        clearAllMocks()
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
            olm = olm,
            store = mockk(),
            key = mockk(),
        )
    }

    should("handle verification step") {
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        coEvery { api.sync.subscribe<VerificationStep>(captureLambda()) }
            .coAnswers { lambda<EventSubscriber<VerificationStep>>().captured.invoke(ToDeviceEvent(cancelEvent, bob)) }
        createCut()
        cut.startLifecycle(this)
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("handle encrypted verification step") {
        createCut()
        cut.startLifecycle(this)
        val cancelEvent = VerificationCancelEventContent(User, "u", null, "t")
        encryptedStepFlow.emit(DecryptedOlmEvent(mockk(), OlmEvent(cancelEvent, bob, mockk(), mockk(), mockk())))
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("send verification step and encrypt it") {
        val encrypted = mockk<EncryptedEventContent.OlmEncryptedEventContent>()
        coEvery { api.users.sendToDevice<VerificationCancelEventContent>(any()) } returns Result.success(Unit)
        coEvery { olm.events.encryptOlm(any(), any(), any()) } returns encrypted
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        coVerify {
            olm.events.encryptOlm(any(), bob, bobDevice)
            api.users.sendToDevice(mapOf(bob to mapOf(bobDevice to encrypted)), any(), any())
        }
    }
    should("send verification step and use unencrypted when encrypt failed") {
        coEvery { api.users.sendToDevice<VerificationCancelEventContent>(any()) } returns Result.success(Unit)
        coEvery { olm.events.encryptOlm(any(), any(), any()) } throws OlmLibraryException(message = "hu")
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        coVerify {
            api.users.sendToDevice<VerificationCancelEventContent>(any(), any(), any())
        }
    }
    should("stop lifecycle, when cancelled") {
        coEvery { api.sync.subscribe<VerificationStep>(captureLambda()) }.coAnswers {
            lambda<EventSubscriber<VerificationStep>>().captured.invoke(
                ToDeviceEvent(VerificationCancelEventContent(User, "u", null, "t"), bob)
            )
        }
        createCut()
        cut.startLifecycle(this)
    }
    should("stop lifecycle, when timed out") {
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("cancel request from other devices") {
        coEvery { api.users.sendToDevice<VerificationCancelEventContent>(any()) } returns Result.success(Unit)
        coEvery { olm.events.encryptOlm(any(), any(), any()) } throws OlmLibraryException(message = "hu")
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
            olm = olm,
            store = mockk(),
            key = mockk(),
        )
        val readyEvent = VerificationReadyEventContent("ALICE_1", setOf(Sas), null, "t")
        coEvery { api.sync.subscribe<VerificationStep>(captureLambda()) }
            .coAnswers { lambda<EventSubscriber<VerificationStep>>().captured.invoke(ToDeviceEvent(readyEvent, alice)) }
        cut.startLifecycle(this)
        cut.state.first { it is ActiveVerificationState.Ready }

        cut.theirDeviceId shouldBe "ALICE_1"
        coVerify {
            api.users.sendToDevice(
                mapOf(
                    alice to mapOf(
                        "ALICE_2" to VerificationCancelEventContent(
                            Accepted, "accepted by other device", null, "t"
                        )
                    )
                ), any()
            )
        }
        cut.cancel()
    }
})