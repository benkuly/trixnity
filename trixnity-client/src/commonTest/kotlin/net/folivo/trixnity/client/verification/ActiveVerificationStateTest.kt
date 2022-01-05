package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import net.folivo.trixnity.client.verification.ActiveVerificationState.Ready
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep

class ActiveVerificationStateTest : ShouldSpec({
    timeout = 30_000

    context(TheirRequest::class.simpleName ?: "Request") {
        context(TheirRequest::ready.name) {
            should("send ${VerificationReadyEventContent::class.simpleName}") {
                var step: VerificationStep? = null
                val cut = TheirRequest(mockk(), "AAAAAA", setOf(Sas), null, "t") { step = it }
                cut.ready()
                step shouldBe VerificationReadyEventContent("AAAAAA", setOf(Sas), null, "t")
            }
        }
    }
    context(Ready::class.simpleName ?: "Ready") {
        context(Ready::start.name) {
            should("send ${SasStartEventContent::class.simpleName}") {
                var step: VerificationStep? = null
                val cut = Ready("AAAAAA", setOf(Sas), null, "t") { step = it }
                cut.start(Sas)
                step shouldBe SasStartEventContent("AAAAAA", relatesTo = null, transactionId = "t")
            }
        }
    }
})