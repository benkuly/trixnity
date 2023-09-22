package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.Unsubscriber
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import net.folivo.trixnity.crypto.olm.OlmDecrypter

class OlmDecrypterMock : OlmDecrypter {
    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        throw NotImplementedError()
    }

    override suspend fun handleOlmEvent(event: Event.ToDeviceEvent<EncryptedEventContent.OlmEncryptedEventContent>) {
        throw NotImplementedError()
    }
}