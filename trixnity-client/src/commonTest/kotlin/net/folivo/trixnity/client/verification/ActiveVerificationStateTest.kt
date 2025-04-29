package net.folivo.trixnity.client.verification

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.verification.ActiveVerificationState.Ready
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class ActiveVerificationStateTest : TrixnityBaseTest() {

    @Test
    fun `TheirRequest » ready » send VerificationReadyEventContent`() = runTest {
        var step: VerificationStep? = null
        val cut = TheirRequest(
            VerificationRequestToDeviceEventContent("", setOf(), 1, ""),
            "AAAAAA", setOf(Sas), null, "t"
        ) { step = it }
        cut.ready()
        step shouldBe VerificationReadyEventContent("AAAAAA", setOf(Sas), null, "t")
    }

    @Test
    fun `Ready » start » send SasStartEventContent`() = runTest {
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