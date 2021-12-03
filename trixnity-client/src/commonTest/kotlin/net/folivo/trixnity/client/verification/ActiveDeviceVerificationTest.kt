package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmService.DecryptedOlmEvent
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.OlmEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.RequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.log.LoggerFactory
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ActiveDeviceVerificationTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"

    val api = mockk<MatrixApiClient>(relaxed = true)
    val olm = mockk<OlmService>(relaxed = true)

    lateinit var cut: ActiveDeviceVerification

    lateinit var stepFlow: MutableSharedFlow<Event<VerificationStep>>
    lateinit var encryptedStepFlow: MutableSharedFlow<DecryptedOlmEvent>

    beforeTest {
        stepFlow = MutableSharedFlow()
        encryptedStepFlow = MutableSharedFlow()
        coEvery { api.sync.events<VerificationStep>() } returns stepFlow
        coEvery { api.json } returns mockk()
        coEvery { olm.decryptedOlmEvents } returns encryptedStepFlow
    }
    afterTest {
        clearAllMocks()
    }

    fun createCut(timestamp: Instant = Clock.System.now()) {
        cut = ActiveDeviceVerification(
            request = RequestEventContent(bobDevice, setOf(Sas), timestamp.toEpochMilliseconds(), "t"),
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            supportedMethods = setOf(Sas),
            api = api,
            olm = olm,
            store = mockk(),
            loggerFactory = LoggerFactory.default
        )
    }

    should("handle verification step") {
        createCut()
        cut.startLifecycle(this)
        val cancelEvent = CancelEventContent(User, "u", null, "t")
        stepFlow.emit(ToDeviceEvent(cancelEvent, bob))
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, bob)
    }
    should("handle encrypted verification step") {
        createCut()
        cut.startLifecycle(this)
        val cancelEvent = CancelEventContent(User, "u", null, "t")
        encryptedStepFlow.emit(DecryptedOlmEvent(mockk(), OlmEvent(cancelEvent, bob, mockk(), mockk(), mockk())))
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, bob)
    }
    should("send verification step and encrypt it") {
        val encrypted = mockk<EncryptedEventContent.OlmEncryptedEventContent>()
        coEvery { api.users.sendToDevice<CancelEventContent>(any()) } just Runs
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
        coEvery { api.users.sendToDevice<CancelEventContent>(any()) } just Runs
        coEvery { olm.events.encryptOlm(any(), any(), any()) } throws OlmLibraryException(message = "hu")
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        coVerify {
            api.users.sendToDevice<CancelEventContent>(any(), any(), any())
        }
    }
    should("stop lifecycle, when cancelled") {
        createCut()
        cut.startLifecycle(this)
        stepFlow.emit(ToDeviceEvent(CancelEventContent(User, "u", null, "t"), bob))
    }
    should("stop lifecycle, when timed out") {
        createCut(Clock.System.now() - Duration.minutes(9.9))
        cut.startLifecycle(this)
    }
})