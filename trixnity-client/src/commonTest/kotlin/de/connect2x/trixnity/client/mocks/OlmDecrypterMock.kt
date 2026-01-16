package de.connect2x.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.core.Unsubscriber
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import de.connect2x.trixnity.crypto.olm.OlmDecrypter

class OlmDecrypterMock : OlmDecrypter {
    val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        eventSubscribers.update { it + eventSubscriber }
        return {
            eventSubscribers.update { it - eventSubscriber }
        }
    }

    override suspend fun handleOlmEvents(events: List<ToDeviceEvent<EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent>>) {
        throw NotImplementedError()
    }
}