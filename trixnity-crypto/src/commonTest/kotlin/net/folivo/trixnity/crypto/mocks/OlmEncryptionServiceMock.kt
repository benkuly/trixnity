package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

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