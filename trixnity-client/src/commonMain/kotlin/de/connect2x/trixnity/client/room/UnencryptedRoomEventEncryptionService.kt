package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent

private val log = Logger("de.connect2x.trixnity.client.room.UnencryptedRoomEventEncryptionService")

class UnencryptedRoomEventEncryptionService(
    private val roomStore: RoomStore,
) : RoomEventEncryptionService {
    override suspend fun encrypt(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>? {
        val room = roomStore.get(roomId).first()
        if (room == null || room.encrypted) return null
        return Result.success(content)
    }

    override suspend fun decrypt(event: ClientEvent.RoomEvent.MessageEvent<*>): Result<MessageEventContent>? {
        if (event.content is EncryptedMessageEventContent) return null
        return Result.success(event.content)
    }
}