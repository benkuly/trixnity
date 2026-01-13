package net.folivo.trixnity.crypto.olm

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class OlmDecrypterTest : TrixnityBaseTest() {

    private val olmEncryptionServiceMock = OlmEncryptionServiceMock()
    private val subscriberReceived = mutableListOf<DecryptedOlmEventContainer>()
    private val subscriber: DecryptedOlmEventSubscriber = {
        subscriberReceived.add(it)
    }

    private val cut = OlmDecrypterImpl(olmEncryptionServiceMock).apply {
        subscribe(subscriber)
    }

    @Test
    fun `ignore exceptions`() = runTest {
        val event = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")),
            UserId("sender", "server")
        )
        olmEncryptionServiceMock.decryptOlm = Result.failure(OlmEncryptionService.DecryptOlmError.ValidationFailed(""))
        cut.handleOlmEvents(listOf(event))
        subscriberReceived shouldHaveSize 0
    }

    @Test
    fun `emit decrypted events`() = runTest {
        val event = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")),
            UserId("sender", "server")
        )
        val decryptedEvent = DecryptedOlmEvent(
            RoomMessageEventContent.TextBased.Text("hi"),
            UserId("sender", "server"), keysOf(),
            null,
            UserId("receiver", "server"), keysOf()
        )
        olmEncryptionServiceMock.decryptOlm = Result.success(decryptedEvent)
        cut.handleOlmEvents(listOf(event))
        subscriberReceived shouldContainExactly listOf(DecryptedOlmEventContainer(event, decryptedEvent))
    }
}