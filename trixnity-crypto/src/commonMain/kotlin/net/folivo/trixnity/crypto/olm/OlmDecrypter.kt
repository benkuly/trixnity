package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.olm.OlmLibraryException

private val log = KotlinLogging.logger {}

data class DecryptedOlmEventContainer(
    val encrypted: Event<OlmEncryptedEventContent>,
    val decrypted: DecryptedOlmEvent<*>
)

typealias DecryptedOlmEventSubscriber = suspend (DecryptedOlmEventContainer) -> Unit

interface IOlmDecrypter {
    fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber)
    fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber)

    suspend fun handleOlmEvent(event: Event<OlmEncryptedEventContent>)
}

class OlmDecrypter(
    private val olmEncryptionService: IOlmEncryptionService,
) : IOlmDecrypter {

    private val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it + eventSubscriber }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it - eventSubscriber }

    override suspend fun handleOlmEvent(event: Event<OlmEncryptedEventContent>) {
        if (event is Event.ToDeviceEvent) {
            val decryptedEvent = try {
                olmEncryptionService.decryptOlm(event.content, event.sender)
            } catch (e: DecryptionException) {
                log.error(e) { "could not decrypt $event" }
                null
            } catch (e: KeyException) {
                log.error(e) { "could not decrypt $event" }
                null
            } catch (e: OlmLibraryException) {
                log.error(e) { "could not decrypt $event" }
                null
            }
            if (decryptedEvent != null)
                eventSubscribers.value.forEach { it(DecryptedOlmEventContainer(event, decryptedEvent)) }
        }
    }
}