package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.Unsubscriber
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventSubscriber
import de.connect2x.trixnity.crypto.olm.OlmDecrypter

class OlmDecrypterMock : OlmDecrypter {
    override fun subscribe(eventSubscriber: DecryptedOlmEventSubscriber): Unsubscriber {
        throw NotImplementedError()
    }

    override suspend fun handleOlmEvents(events: List<ToDeviceEvent<EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent>>) {
        throw NotImplementedError()
    }
}