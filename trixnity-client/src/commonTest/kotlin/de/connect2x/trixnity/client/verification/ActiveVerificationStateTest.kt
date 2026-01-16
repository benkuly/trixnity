package de.connect2x.trixnity.client.verification

import io.kotest.matchers.shouldBe
import de.connect2x.trixnity.client.verification.ActiveVerificationState.Ready
import de.connect2x.trixnity.client.verification.ActiveVerificationState.TheirRequest
import de.connect2x.trixnity.core.model.events.m.key.verification.*
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
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