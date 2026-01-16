package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.*
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService

class OlmEncryptionServiceMock : OlmEncryptionService {

    var returnEncryptOlm: Result<OlmEncryptedToDeviceEventContent>? = null
    var encryptOlmCalled: Triple<EventContent, UserId, String>? = null
    override suspend fun encryptOlm(
        content: EventContent,
        userId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): Result<OlmEncryptedToDeviceEventContent> {
        encryptOlmCalled = Triple(content, userId, deviceId)
        return returnEncryptOlm ?: Result.failure(NotImplementedError())
    }

    lateinit var returnDecryptOlm: DecryptedOlmEvent<*>
    override suspend fun decryptOlm(event: ClientEvent.ToDeviceEvent<OlmEncryptedToDeviceEventContent>): Result<DecryptedOlmEvent<*>> {
        return Result.success(returnDecryptOlm)
    }

    var returnEncryptMegolm: Result<MegolmEncryptedMessageEventContent>? = null
    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): Result<MegolmEncryptedMessageEventContent> {
        return returnEncryptMegolm ?: Result.failure(NotImplementedError())
    }

    val returnDecryptMegolm = mutableListOf<Result<DecryptedMegolmEvent<*>>>()
    override suspend fun decryptMegolm(encryptedEvent: RoomEvent<MegolmEncryptedMessageEventContent>): Result<DecryptedMegolmEvent<*>> {
        return if (returnDecryptMegolm.size > 1) returnDecryptMegolm.removeFirst()
        else returnDecryptMegolm.first()
    }
}