package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.*
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {
    override suspend fun encryptOlm(
        content: EventContent,
        userId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): Result<OlmEncryptedToDeviceEventContent> {
        throw NotImplementedError()
    }

    var decryptOlm: Result<DecryptedOlmEvent<*>>? = null
    override suspend fun decryptOlm(event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>): Result<DecryptedOlmEvent<*>> =
        decryptOlm ?: throw NotImplementedError()

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): Result<MegolmEncryptedMessageEventContent> {
        throw NotImplementedError()
    }

    override suspend fun decryptMegolm(encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>): Result<DecryptedMegolmEvent<*>> {
        throw NotImplementedError()
    }
}