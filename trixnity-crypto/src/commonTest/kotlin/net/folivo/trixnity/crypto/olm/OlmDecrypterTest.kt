package net.folivo.trixnity.crypto.olm

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.mock.Mocker
import org.kodein.mock.UsesMocks

@UsesMocks(IOlmEncryptionService::class)
class OlmDecrypterTest : ShouldSpec({
    timeout = 60_000

    val mocker = Mocker()
    val mockOlmEventService = MockIOlmEncryptionService(mocker)

    val subscriberReceived = mutableListOf<DecryptedOlmEventContainer>()
    val subscriber: DecryptedOlmEventSubscriber = {
        subscriberReceived.add(it)
    }

    val cut = OlmDecrypter(mockOlmEventService)
    cut.subscribe(subscriber)

    afterEach {
        subscriberReceived.clear()
        mocker.reset()
    }

    should("catch DecryptionException") {
        val event = Event.ToDeviceEvent(
            EncryptedEventContent.OlmEncryptedEventContent(
                mapOf(), Key.Curve25519Key(null, "")
            ),
            UserId("sender", "server")
        )
        mocker.everySuspending {
            mockOlmEventService.decryptOlm(isAny(), isAny())
        } runs { throw DecryptionException.ValidationFailed("whoops") }
        cut(event)
        subscriberReceived shouldHaveSize 0
    }
    should("catch KeyException") {
        val event = Event.ToDeviceEvent(
            EncryptedEventContent.OlmEncryptedEventContent(
                mapOf(), Key.Curve25519Key(null, "")
            ),
            UserId("sender", "server")
        )
        mocker.everySuspending {
            mockOlmEventService.decryptOlm(isAny(), isAny())
        } runs { throw KeyException.KeyNotFoundException("not found") }
        cut(event)
        subscriberReceived shouldHaveSize 0
    }
    should("catch OlmLibraryException") {
        val event = Event.ToDeviceEvent(
            EncryptedEventContent.OlmEncryptedEventContent(
                mapOf(), Key.Curve25519Key(null, "")
            ),
            UserId("sender", "server")
        )
        mocker.everySuspending {
            mockOlmEventService.decryptOlm(isAny(), isAny())
        } runs { throw OlmLibraryException("something") }
        cut(event)
        subscriberReceived shouldHaveSize 0
    }
    should("emit decrypted events") {
        val event = Event.ToDeviceEvent(
            EncryptedEventContent.OlmEncryptedEventContent(
                mapOf(), Key.Curve25519Key(null, "")
            ),
            UserId("sender", "server")
        )
        val decryptedEvent = DecryptedOlmEvent(
            TextMessageEventContent("hi"),
            UserId("sender", "server"), keysOf(),
            UserId("receiver", "server"), keysOf()
        )
        mocker.everySuspending { mockOlmEventService.decryptOlm(isAny(), isAny()) } returns decryptedEvent
        cut(event)
        subscriberReceived shouldContainExactly listOf(DecryptedOlmEventContainer(event, decryptedEvent))
    }
})