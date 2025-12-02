package net.folivo.trixnity.client.verification

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.OwnSasStart
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.TheirSasStart
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Unknown
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class ActiveVerificationTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BBBBBB"
    private val lifecycleCalled = MutableStateFlow(0)
    private val sendVerificationStepFlow = MutableSharedFlow<VerificationStep>(replay = 10)

    private val keyStore = getInMemoryKeyStore()
    private val cut = TestActiveVerification(
        VerificationRequestToDeviceEventContent(
            bobDevice,
            setOf(Sas),
            1234,
            "t"
        ),
        keyStore
    )

    @Test
    fun `startLifecycle » start lifecycle once`() = runTest {
        cut.startLifecycle(this)
        cut.startLifecycle(this)
        lifecycleCalled.value shouldBe 1
    }

    @Test
    fun `cancel » send user cancel event content`() = runTest {
        val expectedCancelEvent =
            VerificationCancelEventContent(Code.User, "user cancelled verification", null, "t")
        cut.cancel()
        sendVerificationStepFlow.first() shouldBe expectedCancelEvent
        cut.state.value shouldBe Cancel(expectedCancelEvent, true)
    }

    @Test
    fun `handleVerificationStep » step is from foreign user » cancel`() = runTest {
        cut.handleStep(
            VerificationReadyEventContent("FFFFFF", setOf(), null, "t"),
            UserId("f", "server"),
            false
        )
        sendVerificationStepFlow.first().shouldBeInstanceOf<VerificationCancelEventContent>()
    }

    @Test
    fun `handleVerificationStep » step has no matching transaction » cancel`() = runTest {
        cut.handleStep(VerificationReadyEventContent(aliceDevice, setOf(), null, null), alice, true)
        sendVerificationStepFlow.first().shouldBeInstanceOf<VerificationCancelEventContent>()
    }

    @Test
    fun `handleVerificationStep » current state is AcceptedByOtherDevice » set state to Done when done`() = runTest {
        cut.setState(AcceptedByOtherDevice)
        cut.handleStep(VerificationDoneEventContent(null, "t"), alice, false)
        cut.state.value shouldBe Done
    }

    @Test
    fun `handleVerificationStep » current state is AcceptedByOtherDevice » set state to Cancel when cancel`() =
        runTest {
            cut.setState(AcceptedByOtherDevice)
            val cancelEvent = VerificationCancelEventContent(Code.User, "user", null, "t")
            cut.handleStep(cancelEvent, alice, false)
            cut.state.value shouldBe Cancel(cancelEvent, false)
        }

    @Test
    fun `handleVerificationStep » current state is OwnRequest or TheirRequest » cancel unexpected message SasStartEventContent`() =
        checkNotAllowedStateChange(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
        )

    @Test
    fun `handleVerificationStep » current state is OwnRequest or TheirRequest » cancel unexpected message VerificationDoneEventContent`() =
        checkNotAllowedStateChange(VerificationDoneEventContent(null, "t"))

    @Test
    fun `handleVerificationStep » current state is OwnRequest or TheirRequest » handle VerificationReadyEventContent when OwnRequest`() =
        runTest {
            val cut = TestActiveVerification(
                VerificationRequestToDeviceEventContent(aliceDevice, setOf(Sas), 1234, "t"),
                keyStore
            )
            cut.state.value.shouldBeInstanceOf<OwnRequest>()
            cut.handleStep(
                VerificationReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"),
                bob,
                false
            )
            val state = cut.state.value
            state.shouldBeInstanceOf<Ready>()
            state.methods shouldBe setOf(Sas)
            cut.theirDeviceId shouldBe bobDevice
        }

    @Test
    fun `handleVerificationStep » current state is OwnRequest or TheirRequest » handle VerificationReadyEventContent when TheirRequest`() =
        runTest {
            cut.state.value.shouldBeInstanceOf<TheirRequest>()
            cut.handleStep(
                VerificationReadyEventContent(aliceDevice, setOf(Sas, Unknown("u")), null, "t"),
                alice,
                false
            )
            val state = cut.state.value
            state.shouldBeInstanceOf<Ready>()
            state.methods shouldBe setOf(Sas)
        }

    @Test
    fun `handleVerificationStep » current state is Ready » cancel unexpected message VerificationReadyEventContent`() =
        checkNotAllowedStateChange(
            VerificationReadyEventContent(bobDevice, setOf(), null, "t"),
            ::currentStateIsReadySetup,
        )

    @Test
    fun `handleVerificationStep » current state is Ready » cancel unexpected message VerificationDoneEventContent`() =
        checkNotAllowedStateChange(
            VerificationDoneEventContent(null, "t"),
            ::currentStateIsReadySetup,
        )

    @Test
    fun `handleVerificationStep » current state is Ready » handle VerificationStartEventContent`() =
        runTest(setup = { currentStateIsReadySetup() }) {
            val step = SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
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

    @Test
    fun `handleVerificationStep » current state is Start » cancel unexpected message VerificationReadyEventContent`() =
        checkNotAllowedStateChange(
            VerificationReadyEventContent(bobDevice, setOf(), null, "t"),
            ::currentStateIsStartSetup
        )

    @Test
    fun `handleVerificationStep » current state is Start » handle VerificationStartEventContent » keep event from lexicographically smaller user ID`() =
        runTest(setup = { currentStateIsStartSetup() }) {
            val step = SasStartEventContent(
                aliceDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
            cut.handleStep(step, alice, true)
            cut.handleStep(
                SasStartEventContent(
                    bobDevice,
                    hashes = setOf(SasHash.Sha256),
                    keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                    messageAuthenticationCodes = setOf(
                        SasMessageAuthenticationCode.HkdfHmacSha256,
                        SasMessageAuthenticationCode.HkdfHmacSha256V2
                    ),
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    relatesTo = null, transactionId = "t"
                ), bob, false
            )
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

    @Test
    fun `handleVerificationStep » current state is Start » handle VerificationStartEventContent » keep event from lexicographically smaller deviceId`() =
        runTest(setup = { currentStateIsStartSetup() }) {
            cut.handleStep(
                SasStartEventContent(
                    "CCCCCC",
                    hashes = setOf(SasHash.Sha256),
                    keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                    messageAuthenticationCodes = setOf(
                        SasMessageAuthenticationCode.HkdfHmacSha256,
                        SasMessageAuthenticationCode.HkdfHmacSha256V2
                    ),
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    relatesTo = null, transactionId = "t"
                ), bob, false
            )
            val state = cut.state.value
            state.shouldBeInstanceOf<Start>()
            state.senderUserId shouldBe bob
            state.senderDeviceId shouldBe bobDevice
            val method = state.method
            method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
            val subState = method.state.value
            subState.shouldBeInstanceOf<TheirSasStart>()
            subState.content shouldBe SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
        }

    @Test
    fun `handleVerificationStep » current state is Start » handle VerificationStartEventContent » override event from lexicographically smaller user ID`() =
        runTest(setup = { currentStateIsStartSetup() }) {
            val step = SasStartEventContent(
                aliceDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
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

    @Test
    fun `handleVerificationStep » current state is Start » handle VerificationStartEventContent » override event from lexicographically smaller deviceId`() =
        runTest(setup = { currentStateIsStartSetup() }) {
            val step = SasStartEventContent(
                "AAABBB", // do NOT confuse with AAAAAA from alice, but be before BBBBBB lexicographically
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            )
            cut.handleStep(step, bob, false)
            val state = cut.state.value
            state.shouldBeInstanceOf<Start>()
            state.senderUserId shouldBe bob
            state.senderDeviceId shouldBe "AAABBB"
            val method = state.method
            method.shouldBeInstanceOf<ActiveSasVerificationMethod>()
            val subState = method.state.value
            subState.shouldBeInstanceOf<TheirSasStart>()
            subState.content shouldBe step
        }

    @Test
    fun `handleVerificationStep » current state is Start » handle VerificationDoneEventContent`() =
        runTest(setup = { currentStateIsStartSetup() }) {
            val step = VerificationDoneEventContent(null, "t")
            cut.handleStep(step, bob, false)
            val state = cut.state.value
            state.shouldBeInstanceOf<WaitForDone>()
            state.isOurOwn shouldBe false
        }

    @Test
    fun `handleVerificationStep » current state is WaitForDone » cancel unexpected message VerificationReadyEventContent`() =
        checkNotAllowedStateChange(
            VerificationReadyEventContent(bobDevice, setOf(), null, "t"),
            ::currentStateIsWaitForDoneSetup,
        )

    @Test
    fun `handleVerificationStep » current state is WaitForDone » cancel unexpected message SasStartEventContent`() =
        checkNotAllowedStateChange(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsWaitForDoneSetup,
        )

    @Test
    fun `handleVerificationStep » current state is WaitForDone » handle VerificationDoneEventContent`() =
        runTest(setup = { currentStateIsWaitForDoneSetup() }) {
            val step = VerificationDoneEventContent(null, "t")
            cut.handleStep(step, alice, true)
            val state = cut.state.value
            state shouldBe Done
        }

    @Test
    fun `handleVerificationStep » current state is Done » cancel unexpected message VerificationReadyEventContent`() =
        checkNotAllowedStateChange(
            VerificationReadyEventContent(bobDevice, setOf(), null, "t"),
            ::currentStateIsDoneSetup
        )

    @Test
    fun `handleVerificationStep » current state is Done » cancel unexpected message SasStartEventContent`() =
        checkNotAllowedStateChange(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsDoneSetup
        )

    @Test
    fun `handleVerificationStep » current state is Done » cancel unexpected message VerificationDoneEventContent`() =
        checkNotAllowedStateChange(
            VerificationDoneEventContent(null, "t"),
            ::currentStateIsDoneSetup
        )

    @Test
    fun `handleVerificationStep » current state is Cancel » cancel unexpected message VerificationReadyEventContent`() =
        checkNotAllowedStateChange(
            VerificationReadyEventContent(bobDevice, setOf(), null, "t"),
            ::currentStateIsCancelSetup
        )

    @Test
    fun `handleVerificationStep » current state is Cancel » cancel unexpected message SasStartEventContent`() =
        checkNotAllowedStateChange(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsCancelSetup
        )

    @Test
    fun `handleVerificationStep » current state is Cancel » cancel unexpected message VerificationDoneEventContent`() =
        checkNotAllowedStateChange(
            VerificationDoneEventContent(null, "t"),
            ::currentStateIsCancelSetup
        )

    @Test
    fun `handleVerificationStep » current state is Cancel » not send multiple cancel events`() =
        runTest(setup = { currentStateIsCancelSetup() }) {
            cut.handleStep(VerificationDoneEventContent(null, "t"), bob, false)
            val state = cut.state.value
            state.shouldBeInstanceOf<Cancel>()
            state.content.code shouldBe Code.UnexpectedMessage
            sendVerificationStepFlow.replayCache.shouldBeEmpty()
        }

    private fun checkNotAllowedStateChange(
        step: VerificationStep,
        setup: suspend () -> Unit = {},
    ) = runTest(setup = { setup() }) {
        val stateBefore = cut.state.value
        cut.handleStep(step, bob, false)
        val state = cut.state.value
        state.shouldBeInstanceOf<Cancel>()
        state.content.code shouldBe Code.UnexpectedMessage
        if (stateBefore !is Cancel) {
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<VerificationCancelEventContent>()
            result.code shouldBe Code.UnexpectedMessage
        }
    }

    private suspend fun currentStateIsStartSetup() {
        cut.handleStep(
            VerificationReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"),
            bob,
            false
        )
        cut.handleStep(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), bob, false
        )
        cut.state.value.shouldBeInstanceOf<Start>()
    }

    private suspend fun currentStateIsReadySetup() {
        cut.handleStep(
            VerificationReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"),
            bob,
            false
        )
        cut.state.value.shouldBeInstanceOf<Ready>()
    }

    private suspend fun currentStateIsWaitForDoneSetup() {
        cut.handleStep(
            VerificationReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"),
            bob,
            false
        )
        cut.handleStep(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), bob, false
        )
        cut.handleStep(VerificationDoneEventContent(null, "t"), bob, false)
        cut.state.value.shouldBeInstanceOf<WaitForDone>()
    }

    private suspend fun currentStateIsDoneSetup() {
        cut.handleStep(
            VerificationReadyEventContent(bobDevice, setOf(Sas, Unknown("u")), null, "t"),
            bob,
            false
        )
        cut.handleStep(
            SasStartEventContent(
                bobDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256,
                    SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), bob, false
        )
        cut.handleStep(VerificationDoneEventContent(null, "t"), bob, false)
        cut.handleStep(VerificationDoneEventContent(null, "t"), alice, true)
        cut.state.value.shouldBeInstanceOf<Done>()
    }

    private suspend fun currentStateIsCancelSetup() {
        cut.handleStep(VerificationCancelEventContent(Code.User, "user", null, "t"), bob, false)
        cut.state.value.shouldBeInstanceOf<Cancel>()
    }

    private inner class TestActiveVerification(request: VerificationRequestToDeviceEventContent, keyStore: KeyStore) :
        ActiveVerificationImpl(
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
            keyStore,
            KeyTrustServiceMock(),
            createMatrixEventJson(),
            driver,
        ) {
        override suspend fun lifecycle() {
            lifecycleCalled.value++
        }

        override suspend fun sendVerificationStep(step: VerificationStep) {
            sendVerificationStepFlow.emit(step)
        }

        suspend fun handleStep(step: VerificationStep, sender: UserId, isOurOwn: Boolean) {
            super.handleVerificationStep(step, sender, isOurOwn)
        }

        fun setState(state: ActiveVerificationState) {
            mutableState.value = state
        }
    }
}