package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent

private val log = KotlinLogging.logger("net.folivo.trixnity.client.room.UnencryptedRoomEventEncryptionService")

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