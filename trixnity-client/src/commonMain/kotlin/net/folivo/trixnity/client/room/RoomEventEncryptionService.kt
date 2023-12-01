package net.folivo.trixnity.client.room

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent

interface RoomEventEncryptionService {
    /**
     * Encrypts given event. Returns null, when encryption algorithm is not supported by this service.
     */
    suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>?

    /**
     * Decrypts given event. Returns null, when encryption algorithm is not supported by this service.
     */
    suspend fun decrypt(event: RoomEvent<*>): Result<RoomEventContent>?
}