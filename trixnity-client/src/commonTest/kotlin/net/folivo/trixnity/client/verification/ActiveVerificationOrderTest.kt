package net.folivo.trixnity.client.verification

import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.core.MacValue
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.SasMacEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationDoneEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveVerificationOrderTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BBBBBB"

    private val sentEvents = mutableListOf<VerificationStep>()
    private val handledEvents = mutableListOf<VerificationStep>()

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
    fun `events are handled in order`() = runTest {
        val macEvent = SasMacEventContent(MacValue("k"), Keys(), cut.relatesTo, cut.transactionId)
        cut.sendAndHandleVerificationStep(macEvent)
        assertEquals(
            expected = listOf(macEvent, VerificationDoneEventContent(cut.relatesTo, cut.transactionId)),
            actual = handledEvents,
        )
    }

    @Test
    fun `events are sent in order`() = runTest {
        val macEvent = SasMacEventContent(MacValue("k"), Keys(), cut.relatesTo, cut.transactionId)
        cut.sendAndHandleVerificationStep(macEvent)
        assertEquals(
            expected = listOf(macEvent, VerificationDoneEventContent(cut.relatesTo, cut.transactionId)),
            actual = sentEvents,
        )
    }

    @Test
    fun `events are sent and handled in the same order`() = runTest {
        val macEvent = SasMacEventContent(MacValue("k"), Keys(), cut.relatesTo, cut.transactionId)
        cut.sendAndHandleVerificationStep(macEvent)
        assertEquals(handledEvents, sentEvents)
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
        override suspend fun lifecycle() = Unit

        suspend fun sendAndHandleVerificationStep(step: VerificationStep) {
            this.queueStep(step)
        }

        override suspend fun sendVerificationStep(step: VerificationStep) {
            sentEvents.add(step)
        }

        override suspend fun handleVerificationStep(step: VerificationStep, sender: UserId, isOurOwn: Boolean) {
            handledEvents.add(step)
            when (step) {
                is SasMacEventContent -> {
                    sendAndHandleVerificationStep(VerificationDoneEventContent(relatesTo, transactionId))
                }
            }
        }
    }
}