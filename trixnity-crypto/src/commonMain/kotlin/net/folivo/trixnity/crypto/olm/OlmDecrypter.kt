package net.folivo.trixnity.crypto.olm

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent

private val log = KotlinLogging.logger {}

data class DecryptedOlmEventContainer(
    val encrypted: Event<OlmEncryptedEventContent>,
    val decrypted: DecryptedOlmEvent<*>
)

typealias DecryptedOlmEventSubscriber = suspend (DecryptedOlmEventContainer) -> Unit

interface OlmDecrypter {
    fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber)
    fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber)

    suspend fun handleOlmEvent(event: Event<OlmEncryptedEventContent>)
}

class OlmDecrypterImpl(
    private val olmEncryptionService: OlmEncryptionService,
) : OlmDecrypter {

    private val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it + eventSubscriber }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it - eventSubscriber }

    override suspend fun handleOlmEvent(event: Event<OlmEncryptedEventContent>) {
        if (event is Event.ToDeviceEvent) {
            val decryptedEvent = try {
                olmEncryptionService.decryptOlm(event.content, event.sender)
            } catch (e: Exception) {
                log.error(e) { "could not decrypt $event" }
                null
            }
            if (decryptedEvent != null)
                eventSubscribers.value.forEach { it(DecryptedOlmEventContainer(event, decryptedEvent)) }
        }
    }
}