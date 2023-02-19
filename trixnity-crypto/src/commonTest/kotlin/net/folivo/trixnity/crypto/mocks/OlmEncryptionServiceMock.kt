package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {
    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): EncryptedEventContent.OlmEncryptedEventContent {
        TODO("Not yet implemented")
    }

    lateinit var decryptOlm:(()->DecryptedOlmEvent<*>)
    override suspend fun decryptOlm(
        encryptedContent: EncryptedEventContent.OlmEncryptedEventContent,
        senderId: UserId
    ): DecryptedOlmEvent<*> =decryptOlm()

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): EncryptedEventContent.MegolmEncryptedEventContent {
        TODO("Not yet implemented")
    }

    override suspend fun decryptMegolm(encryptedEvent: Event.RoomEvent<EncryptedEventContent.MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        TODO("Not yet implemented")
    }
}