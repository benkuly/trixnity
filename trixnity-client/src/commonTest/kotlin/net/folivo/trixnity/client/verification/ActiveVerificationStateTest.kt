package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import net.folivo.trixnity.client.verification.ActiveVerificationState.Ready
import net.folivo.trixnity.client.verification.ActiveVerificationState.Request
import net.folivo.trixnity.core.model.events.m.key.verification.ReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep

class ActiveVerificationStateTest : ShouldSpec({
    timeout = 30_000

    context(Request::class.simpleName ?: "Request") {
        context(Request::ready.name) {
            should("send ${ReadyEventContent::class.simpleName}") {
                var step: VerificationStep? = null
                val cut = Request(mockk(), "AAAAAA", setOf(Sas), null, "t") { step = it }
                cut.ready()
                step shouldBe ReadyEventContent("AAAAAA", setOf(Sas), null, "t")
            }
        }
    }
    context(Ready::class.simpleName ?: "Ready") {
        context(Request::ready.name) {
            should("send ${SasStartEventContent::class.simpleName}") {
                var step: VerificationStep? = null
                val cut = Ready("AAAAAA", setOf(Sas), null, "t") { step = it }
                cut.start(Sas)
                step shouldBe SasStartEventContent("AAAAAA", relatesTo = null, transactionId = "t")
            }
        }
    }
})