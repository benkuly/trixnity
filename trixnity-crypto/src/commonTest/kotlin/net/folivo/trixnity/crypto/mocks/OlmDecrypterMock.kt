package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.IOlmDecrypter

class OlmDecrypterMock : IOlmDecrypter {
    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber) {
        TODO("Not yet implemented")
    }

    override fun unsubscribe(eventSubscriber: DecryptedOlmEventSubscriber) {
        TODO("Not yet implemented")
    }

    override suspend fun invoke(p1: Event<EncryptedEventContent.OlmEncryptedEventContent>) {
        TODO("Not yet implemented")
    }
}