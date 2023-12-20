package net.folivo.trixnity.client.room

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomEventContent

class UnencryptedRoomEventEncryptionService(
    private val roomStore: RoomStore,
) : RoomEventEncryptionService {
    override suspend fun encrypt(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>? {
        if (roomStore.get(roomId).first()?.encrypted != false) return null
        return Result.success(content)
    }

    override suspend fun decrypt(event: ClientEvent.RoomEvent<*>): Result<RoomEventContent>? {
        if (roomStore.get(event.roomId).first()?.encrypted != false) return null
        return Result.success(event.content)
    }
}