package net.folivo.trixnity.crypto.olm

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.mocks.OlmEncryptionServiceMock

class OlmDecrypterTest : ShouldSpec({
    timeout = 60_000

    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock

    val subscriberReceived = mutableListOf<DecryptedOlmEventContainer>()
    val subscriber: DecryptedOlmEventSubscriber = {
        subscriberReceived.add(it)
    }

    lateinit var cut: OlmDecrypterImpl
    beforeTest {
        subscriberReceived.clear()
        olmEncryptionServiceMock = OlmEncryptionServiceMock()
        cut = OlmDecrypterImpl(olmEncryptionServiceMock)
        cut.subscribe(subscriber)
    }



    should("ignore exceptions") {
        val event = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")),
            UserId("sender", "server")
        )
        olmEncryptionServiceMock.decryptOlm = Result.failure(OlmEncryptionService.DecryptOlmError.ValidationFailed(""))
        cut.handleOlmEvents(listOf(event))
        subscriberReceived shouldHaveSize 0
    }
    should("emit decrypted events") {
        val event = ToDeviceEvent(
            OlmEncryptedToDeviceEventContent(mapOf(), Curve25519KeyValue("")),
            UserId("sender", "server")
        )
        val decryptedEvent = DecryptedOlmEvent(
            RoomMessageEventContent.TextBased.Text("hi"),
            UserId("sender", "server"), keysOf(),
            UserId("receiver", "server"), keysOf()
        )
        olmEncryptionServiceMock.decryptOlm = Result.success(decryptedEvent)
        cut.handleOlmEvents(listOf(event))
        subscriberReceived shouldContainExactly listOf(DecryptedOlmEventContainer(event, decryptedEvent))
    }
})