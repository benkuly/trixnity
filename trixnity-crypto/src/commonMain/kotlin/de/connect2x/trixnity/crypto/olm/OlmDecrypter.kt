package de.connect2x.trixnity.crypto.olm

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import de.connect2x.trixnity.core.Unsubscriber
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.DecryptedOlmEvent
import de.connect2x.trixnity.core.model.events.Event
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent

data class DecryptedOlmEventContainer(
    val encrypted: Event<OlmEncryptedToDeviceEventContent>,
    val decrypted: DecryptedOlmEvent<*>
)

typealias DecryptedOlmEventSubscriber = suspend (DecryptedOlmEventContainer) -> Unit

interface OlmDecrypter {
    fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber

    suspend fun handleOlmEvents(events: List<ToDeviceEvent<OlmEncryptedToDeviceEventContent>>)
}

class OlmDecrypterImpl(
    private val olmEncryptionService: OlmEncryptionService,
) : OlmDecrypter {

    private val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        eventSubscribers.update { it + eventSubscriber }
        return {
            eventSubscribers.update { it - eventSubscriber }
        }
    }


    override suspend fun handleOlmEvents(events: List<ToDeviceEvent<OlmEncryptedToDeviceEventContent>>) =
        coroutineScope {
            events.groupBy { it.sender to it.content.senderKey }
                .forEach { (_, events) ->
                    launch {
                        events.forEach { event ->
                            val decryptedEvent = olmEncryptionService.decryptOlm(event).getOrNull()
                            if (decryptedEvent != null) {
                                eventSubscribers.value.forEach {
                                    it(DecryptedOlmEventContainer(event, decryptedEvent))
                                }
                            }
                        }
                    }
                }
        }
}