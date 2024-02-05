package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

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