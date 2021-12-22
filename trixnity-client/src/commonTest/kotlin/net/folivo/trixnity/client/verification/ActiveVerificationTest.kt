package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.OwnSasStart
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.TheirSasStart
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Unknown

class ActiveVerificationTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"
    var lifecycleCalled = 0
    lateinit var sendVerificationStepFlow: MutableSharedFlow<VerificationStep>

    class TestActiveVerification(request: RequestEventContent) : ActiveVerification(
        request = request,
        requestIsFromOurOwn = request.fromDevice == aliceDevice,
        ownUserId = alice,
        ownDeviceId = aliceDevice,
        theirUserId = bob,
        theirInitialDeviceId = null,
        timestamp = 1234,
        setOf(Sas),
        null,
        "t",
        mockk(),
        mockk(),
        mockk(),
    ) {
        override suspend fun lifecycle(scope: CoroutineScope) {
            lifecycleCalled++
        }

        override suspend fun sendVerificationStep(step: VerificationStep) {
            sendVerificationStepFlow.emit(step)
        }

        suspend fun handleStep(step: VerificationStep, sender: UserId, isOurOwn: Boolean) {
            super.handleIncomingVerificationStep(step, sender, isOurOwn)
        }
    }

    lateinit var cut: TestActiveVerification
    beforeTest {
        lifecycleCalled = 0
        sendVerificationStepFlow = MutableSharedFlow(replay = 10)
        cut = TestActiveVerification(RequestEventContent(bobDevice, setOf(Sas), 1234, "t"))
    }

    context(ActiveVerification::startLifecycle.name) {
        should("start lifecycle once") {
            cut.startLifecycle(this)
            cut.startLifecycle(this)
            lifecycleCalled shouldBe 1
        }
    }
    context(ActiveVerification::cancel.name) {
        should("send user cancel event content") {
            val expectedCancelEvent = CancelEventContent(Code.User, "user cancelled verification", null, "t")
            cut.cancel()
            sendVerificationStepFlow.first() shouldBe expectedCancelEvent
            cut.state.value shouldBe Cancel(expectedCancelEvent, alice)
        }
    }
    context("handleVerificationStep") {
        context("step is from foreign user") {
            should("cancel") {
                cut.handleStep(ReadyEventContent("FFFFFF", setOf(), null, "t"), UserId("f", "server"), false)
                sendVerificationStepFlow.first().shouldBeInstanceOf<CancelEventContent>()
            }
        }
        context("step has no matching transaction") {
            should("cancel") {
                cut.handleStep(ReadyEventContent(aliceDevice, setOf(), null, null), alice, true)
                sendVerificationStepFlow.first().shouldBeInstanceOf<CancelEventContent>()
            }
        }
        suspend fun ShouldSpecContainerScope.checkNotAllowedStateChange(vararg steps: VerificationStep) {
            steps.forEach {
                should("cancel unexpected message ${it::class.simpleName}") {
                    val stateBefore = cut.state.value
                    cut.handleStep(it, bob, false)
                    val state = cut.state.value
                    state.shouldBeInstanceOf<Cancel>()
                    state.content.code shouldBe Code.UnexpectedMessage
                    if (stateBefore !is Cancel) {
                        val result = sendVerificationStepFlow.first()
                        result.shouldBeInstanceOf<CancelEventContent>()
                        result.code shouldBe Code.UnexpectedMessage
                    }
                }
            }
        }
        context("current state is ${OwnRequest::class.simpleName} or ${TheirRequest::class.simpleName}") {
            checkNotAllowedStateChange(
                SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"),
                DoneEventContent(null, "t"),
            )
            should("handle ${ReadyEventContent::class.simpleName} when ${OwnRequest::class.simpleName}") {
                cut = TestActiveVerification(RequestEventContent(aliceDevice, setOf(Sas), 1234, "t"))
                cut.state.value.shouldBeInstanceOf<OwnRequest>()
                cut.handleStep(ReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"), bob, false)
                val state = cut.state.value
                state.shouldBeInstanceOf<Ready>()
                state.methods shouldBe setOf(Sas)
                cut.theirDeviceId shouldBe bobDevice
            }
            should("handle ${ReadyEventContent::class.simpleName} when ${TheirRequest::class.simpleName}") {
                cut.state.value.shouldBeInstanceOf<TheirRequest>()
                cut.handleStep(ReadyEventContent(aliceDevice, setOf(Sas, Unknown("u")), null, "t"), alice, false)
                val state = cut.state.value
                state.shouldBeInstanceOf<Ready>()
                state.methods shouldBe setOf(Sas)
            }
        }
        context("current state is ${Ready::class.simpleName}") {
            beforeTest {
                cut.handleStep(ReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"), bob, false)
                cut.state.value.shouldBeInstanceOf<Ready>()
            }
            checkNotAllowedStateChange(
                ReadyEventContent(bobDevice, setOf(), null, "t"),
                DoneEventContent(null, "t"),
            )
            should("handle ${StartEventContent::class.simpleName}") {
                val step = SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t")
                cut.handleStep(step, bob, false)
                val state = cut.state.value
                state.shouldBeInstanceOf<Start>()
                state.senderUserId shouldBe bob
                state.senderDeviceId shouldBe bobDevice
                val method = state.method
                method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                val subState = method.state.value
                subState.shouldBeInstanceOf<TheirSasStart>()
                subState.content shouldBe step
            }
        }
        context("current state is ${Start::class.simpleName}") {
            beforeTest {
                cut.handleStep(ReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"), bob, false)
                cut.handleStep(SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"), bob, false)
                cut.state.value.shouldBeInstanceOf<Start>()
            }
            checkNotAllowedStateChange(
                ReadyEventContent(bobDevice, setOf(), null, "t"),
            )
            context("handle ${StartEventContent::class.simpleName}") {
                should("keep event from lexicographically smaller user ID") {
                    val step = SasStartEventContent(aliceDevice, relatesTo = null, transactionId = "t")
                    cut.handleStep(step, alice, true)
                    cut.handleStep(SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"), bob, false)
                    val state = cut.state.value
                    state.shouldBeInstanceOf<Start>()
                    state.senderUserId shouldBe alice
                    state.senderDeviceId shouldBe aliceDevice
                    val method = state.method
                    method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    val subState = method.state.value
                    subState.shouldBeInstanceOf<OwnSasStart>()
                    subState.content shouldBe step
                }
                should("keep event from lexicographically smaller deviceId") {
                    cut.handleStep(SasStartEventContent("CCCCCC", relatesTo = null, transactionId = "t"), bob, false)
                    val state = cut.state.value
                    state.shouldBeInstanceOf<Start>()
                    state.senderUserId shouldBe bob
                    state.senderDeviceId shouldBe bobDevice
                    val method = state.method
                    method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    val subState = method.state.value
                    subState.shouldBeInstanceOf<TheirSasStart>()
                    subState.content shouldBe SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t")
                }
                should("override event from lexicographically smaller user ID") {
                    val step = SasStartEventContent(aliceDevice, relatesTo = null, transactionId = "t")
                    cut.handleStep(step, alice, true)
                    val state = cut.state.value
                    state.shouldBeInstanceOf<Start>()
                    state.senderUserId shouldBe alice
                    state.senderDeviceId shouldBe aliceDevice
                    val method = state.method
                    method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    val subState = method.state.value
                    subState.shouldBeInstanceOf<OwnSasStart>()
                    subState.content shouldBe step
                }
                should("override event from lexicographically smaller deviceId") {
                    val step = SasStartEventContent("AAAAAA", relatesTo = null, transactionId = "t")
                    cut.handleStep(step, bob, false)
                    val state = cut.state.value
                    state.shouldBeInstanceOf<Start>()
                    state.senderUserId shouldBe bob
                    state.senderDeviceId shouldBe "AAAAAA"
                    val method = state.method
                    method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
                    val subState = method.state.value
                    subState.shouldBeInstanceOf<TheirSasStart>()
                    subState.content shouldBe step
                }
            }
            should("handle ${DoneEventContent::class.simpleName}") {
                val step = DoneEventContent(null, "t")
                cut.handleStep(step, bob, false)
                val state = cut.state.value
                state.shouldBeInstanceOf<PartlyDone>()
                state.isOurOwn shouldBe false
            }
        }
        context("current state is ${PartlyDone::class.simpleName}") {
            beforeTest {
                cut.handleStep(ReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"), bob, false)
                cut.handleStep(SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"), bob, false)
                cut.handleStep(DoneEventContent(null, "t"), bob, false)
                cut.state.value.shouldBeInstanceOf<PartlyDone>()
            }
            checkNotAllowedStateChange(
                ReadyEventContent(bobDevice, setOf(), null, "t"),
                SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"),
            )
            should("handle ${DoneEventContent::class.simpleName}") {
                val step = DoneEventContent(null, "t")
                cut.handleStep(step, alice, true)
                val state = cut.state.value
                state shouldBe Done
            }
        }
        context("current state is ${Done::class.simpleName}") {
            beforeTest {
                cut.handleStep(ReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"), bob, false)
                cut.handleStep(SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"), bob, false)
                cut.handleStep(DoneEventContent(null, "t"), bob, false)
                cut.handleStep(DoneEventContent(null, "t"), alice, true)
                cut.state.value.shouldBeInstanceOf<Done>()
            }
            checkNotAllowedStateChange(
                ReadyEventContent(bobDevice, setOf(), null, "t"),
                SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"),
                DoneEventContent(null, "t")
            )
        }
        context("current state is ${Cancel::class.simpleName}") {
            beforeTest {
                cut.handleStep(CancelEventContent(Code.User, "user", null, "t"), bob, false)
                cut.state.value.shouldBeInstanceOf<Cancel>()
            }
            checkNotAllowedStateChange(
                ReadyEventContent(bobDevice, setOf(), null, "t"),
                SasStartEventContent(bobDevice, relatesTo = null, transactionId = "t"),
                DoneEventContent(null, "t"),
            )
            should("not send multiple cancel events") {
                cut.handleStep(DoneEventContent(null, "t"), bob, false)
                val state = cut.state.value
                state.shouldBeInstanceOf<Cancel>()
                state.content.code shouldBe Code.UnexpectedMessage
                sendVerificationStepFlow.replayCache.shouldBeEmpty()
            }
        }
    }
})