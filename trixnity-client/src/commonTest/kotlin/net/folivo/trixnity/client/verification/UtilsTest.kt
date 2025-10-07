package net.folivo.trixnity.client.verification

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.keys.KeyValue
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class UtilsTest : TrixnityBaseTest() {

    init {
        testScope.testScheduler.advanceTimeBy(10.days)
    }

    @Test
    fun `isVerificationRequestActive » return false when older then 10 minutes`() = runTest {
        isVerificationRequestActive(
            (testClock.now() - 11.minutes).toEpochMilliseconds(),
            testClock
        ) shouldBe false
    }

    @Test
    fun `isVerificationRequestActive » return false when older newer 10 minutes`() = runTest {
        isVerificationRequestActive(
            (testClock.now() + 6.minutes).toEpochMilliseconds(),
            testClock
        ) shouldBe false
    }

    @Test
    fun `isVerificationRequestActive » return false when state is cancel`() = runTest {
        isVerificationRequestActive(
            testClock.now().toEpochMilliseconds(),
            testClock,
            ActiveVerificationState.Cancel(VerificationCancelEventContent(Code.User, "", null, null), false)
        ) shouldBe false
    }

    @Test
    fun `isVerificationRequestActive » return false when state is done`() = runTest {
        isVerificationRequestActive(
            testClock.now().toEpochMilliseconds(),
            testClock,
            ActiveVerificationState.Done
        ) shouldBe false
    }

    @Test
    fun `isVerificationRequestActive » return true when active`() = runTest {
        isVerificationRequestActive(
            testClock.now().toEpochMilliseconds(),
            testClock,
            ActiveVerificationState.Ready("", setOf(), null, "t") {}
        ) shouldBe true
    }

    @Test
    fun `createSasCommitment » create sas commitment`() = runTest {
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