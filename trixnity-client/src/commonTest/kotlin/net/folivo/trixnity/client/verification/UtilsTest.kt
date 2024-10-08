package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.time.Duration.Companion.minutes

class UtilsTest : ShouldSpec({
    timeout = 30_000

    context("isVerificationRequestActive") {
        should("return false, when older then 10 minutes") {
            isVerificationRequestActive(
                (Clock.System.now() - 11.minutes).toEpochMilliseconds(),
                Clock.System
            ) shouldBe false
        }
        should("return false, when older newer 10 minutes") {
            isVerificationRequestActive(
                (Clock.System.now() + 6.minutes).toEpochMilliseconds(),
                Clock.System
            ) shouldBe false
        }
        should("return false, when state is cancel") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                Clock.System,
                ActiveVerificationState.Cancel(VerificationCancelEventContent(Code.User, "", null, null), false)
            ) shouldBe false
        }
        should("return false, when state is done") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                Clock.System,
                ActiveVerificationState.Done
            ) shouldBe false
        }
        should("return true, when active") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                Clock.System,
                ActiveVerificationState.Ready("", setOf(), null, "t") {}
            ) shouldBe true
        }
    }
    context(::createSasCommitment.name) {
        should("create sas commitment") {
            createSasCommitment(
                "publicKey",
                VerificationStartEventContent.SasStartEventContent(
                    "AAAAAA",
                    hashes = setOf(SasHash.Sha256),
                    keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                    messageAuthenticationCodes = setOf(
                        SasMessageAuthenticationCode.HkdfHmacSha256,
                    ),
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    transactionId = "transaction",
                    relatesTo = null
                ),
                createMatrixEventJson()
            ) shouldBe "+w/v2hp3vXNFmn3RKqUKzq/BzSRwE8WzX5fNC83LFLE"
        }
    }
})