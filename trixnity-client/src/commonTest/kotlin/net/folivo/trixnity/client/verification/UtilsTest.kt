package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.datetime.Clock
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UtilsTest : ShouldSpec({
    timeout = 30_000

    context("isVerificationRequestActive") {
        should("return false, when older then 10 minutes") {
            isVerificationRequestActive((Clock.System.now() - 11.minutes).toEpochMilliseconds()) shouldBe false
        }
        should("return false, when older newer 10 minutes") {
            isVerificationRequestActive((Clock.System.now() + 6.minutes).toEpochMilliseconds()) shouldBe false
        }
        should("return false, when state is cancel") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                ActiveVerificationState.Cancel(mockk(), mockk())
            ) shouldBe false
        }
        should("return false, when state is done") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                ActiveVerificationState.Done
            ) shouldBe false
        }
        should("return true, when active") {
            isVerificationRequestActive(
                Clock.System.now().toEpochMilliseconds(),
                ActiveVerificationState.Ready("", setOf(), null, "t") {}
            ) shouldBe true
        }
    }
    context(::createSasCommitment.name) {
        should("create sas commitment") {
            createSasCommitment(
                "publicKey",
                StartEventContent.SasStartEventContent("AAAAAA", transactionId = "transaction", relatesTo = null),
                createMatrixJson()
            ) shouldBe "+w/v2hp3vXNFmn3RKqUKzq/BzSRwE8WzX5fNC83LFLE"
        }
    }
})