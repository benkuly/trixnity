package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.crypto.olm.IOlmEncryptionService

class OlmEncryptionServiceMock : IOlmEncryptionService {

    lateinit var returnEncryptOlm: () -> EncryptedEventContent.OlmEncryptedEventContent
    var encryptOlmCalled: Triple<EventContent, UserId, String>? = null
    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): EncryptedEventContent.OlmEncryptedEventContent {
        encryptOlmCalled = Triple(content, receiverId, deviceId)
        return returnEncryptOlm()
    }

    lateinit var returnDecryptOlm: DecryptedOlmEvent<*>
    override suspend fun decryptOlm(
        encryptedContent: EncryptedEventContent.OlmEncryptedEventContent,
        senderId: UserId
    ): DecryptedOlmEvent<*> {
        return returnDecryptOlm
    }

    lateinit var returnEncryptMegolm: () -> EncryptedEventContent.MegolmEncryptedEventContent
    override suspend fun encryptMegolm(
        content: RoomEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): EncryptedEventContent.MegolmEncryptedEventContent {
        return returnEncryptMegolm()
    }

    val returnDecryptMegolm = mutableListOf<() -> DecryptedMegolmEvent<*>>()
    override suspend fun decryptMegolm(encryptedEvent: Event.RoomEvent<EncryptedEventContent.MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        val returner =
            if (returnDecryptMegolm.size > 1) returnDecryptMegolm.removeFirst()
            else returnDecryptMegolm.first()
        return returner()
    }
}