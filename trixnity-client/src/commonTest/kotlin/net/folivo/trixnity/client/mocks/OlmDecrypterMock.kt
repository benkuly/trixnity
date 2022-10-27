package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.OlmDecrypter

class OlmDecrypterMock : OlmDecrypter {
    val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it + eventSubscriber }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it - eventSubscriber }

    override suspend fun handleOlmEvent(event: Event<EncryptedEventContent.OlmEncryptedEventContent>) {
        NotImplementedError()
    }
}