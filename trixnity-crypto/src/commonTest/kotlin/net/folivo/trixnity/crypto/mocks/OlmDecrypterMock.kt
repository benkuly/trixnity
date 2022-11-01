package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.OlmDecrypter

class OlmDecrypterMock : OlmDecrypter {
    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) {
        throw NotImplementedError()
    }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) {
        throw NotImplementedError()
    }

    override suspend fun handleOlmEvent(event: Event<EncryptedEventContent.OlmEncryptedEventContent>) {
        throw NotImplementedError()
    }
}