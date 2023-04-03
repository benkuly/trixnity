package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.verification.ActiveVerificationState.Ready
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent

class ActiveVerificationStateTest : ShouldSpec({
    timeout = 30_000

    context(TheirRequest::class.simpleName ?: "Request") {
        context(TheirRequest::ready.name) {
            should("send ${VerificationReadyEventContent::class.simpleName}") {
                var step: VerificationStep? = null
                val cut = TheirRequest(
                    VerificationRequestEventContent("", setOf(), 1, ""),
                    "AAAAAA", setOf(Sas), null, "t"
                ) { step = it }
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
                step shouldBe SasStartEventContent(
                    "AAAAAA",
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
        }
    }
})