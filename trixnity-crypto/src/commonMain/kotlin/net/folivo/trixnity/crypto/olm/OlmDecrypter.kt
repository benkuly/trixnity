package net.folivo.trixnity.crypto.olm

import mu.KotlinLogging
import net.folivo.trixnity.core.EventSubscriber
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent

private val log = KotlinLogging.logger {}

data class DecryptedOlmEventContainer(
    val encrypted: Event<OlmEncryptedEventContent>,
    val decrypted: DecryptedOlmEvent<*>
)

typealias DecryptedOlmEventSubscriber = suspend (DecryptedOlmEventContainer) -> Unit

class OlmDecrypter(
    private val olmEventService: IOlmEventService,
    private vararg val subscribers: DecryptedOlmEventSubscriber
) : EventSubscriber<OlmEncryptedEventContent> {

    override suspend operator fun invoke(event: Event<OlmEncryptedEventContent>) {
        if (event is Event.ToDeviceEvent) {
            val decryptedEvent = try {
                olmEventService.decryptOlm(event.content, event.sender)
            } catch (e: DecryptionException) {
                log.error(e) { "could not decrypt $event" }
                null
            }
            if (decryptedEvent != null) subscribers.forEach { it(DecryptedOlmEventContainer(event, decryptedEvent)) }
        }
    }
}