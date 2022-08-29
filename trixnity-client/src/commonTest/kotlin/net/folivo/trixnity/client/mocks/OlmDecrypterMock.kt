package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.IOlmDecrypter

class OlmDecrypterMock : IOlmDecrypter {
    val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it + eventSubscriber }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it - eventSubscriber }

    override suspend fun invoke(p1: Event<EncryptedEventContent.OlmEncryptedEventContent>) {
        NotImplementedError()
    }
}