package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.delay
import net.folivo.trixnity.client.room.RoomEventEncryptionService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import kotlin.time.Duration

class RoomEventEncryptionServiceMock(dontReturnDecrypt: Boolean = false, private val useInput: Boolean = false) :
    RoomEventEncryptionService {
    var returnEncrypt: Result<MessageEventContent>? = null
    var encryptCounter = 0
    override suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>? {
        encryptCounter++
        return if (useInput) returnEncrypt ?: Result.success(content)
        else returnEncrypt
    }

    var returnDecrypt: Result<RoomEventContent>? = null
    var decryptCounter = 0
    var decryptDelay = if (dontReturnDecrypt) Duration.INFINITE else Duration.ZERO

    override suspend fun decrypt(event: RoomEvent<*>): Result<RoomEventContent>? {
        decryptCounter++
        delay(decryptDelay)
        return if (useInput) returnDecrypt ?: Result.success(event.content)
        else returnDecrypt
    }
}