package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.IOlmDecrypter

class OlmDecrypterMock : IOlmDecrypter {
    private val eventSubscribers = MutableStateFlow<Set<DecryptedOlmEventSubscriber>>(setOf())

    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it + eventSubscriber }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) =
        eventSubscribers.update { it - eventSubscriber }

    suspend fun emit(event: DecryptedOlmEventContainer) = eventSubscribers.value.forEach { it(event) }
}