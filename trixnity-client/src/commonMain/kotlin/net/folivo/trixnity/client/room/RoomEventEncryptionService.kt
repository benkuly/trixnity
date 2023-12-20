package net.folivo.trixnity.client.room

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent

interface RoomEventEncryptionService {
    /**
     * Encrypts given event. Returns null, when encryption algorithm is not supported by this service.
     *
     * Be aware, that this function can suspend a long time (for example, when the implementation needs to load all members).
     */
    suspend fun encrypt(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent>?

    /**
     * Decrypts given event. Returns null, when encryption algorithm is not supported by this service.
     *
     * Be aware, that this function can suspend a possible infinite time (for example, when the implementation waits for decryption keys).
     */
    suspend fun decrypt(event: RoomEvent<*>): Result<RoomEventContent>?
}