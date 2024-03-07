package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedEventContent

private val log = KotlinLogging.logger { }

class UnencryptedRoomEventEncryptionService(
    private val roomStore: RoomStore,
) : RoomEventEncryptionService {
    override suspend fun encrypt(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>? {
        if (roomStore.get(roomId).first() == null) log.warn { "could not find $roomId in local data, waiting started" }
        if (roomStore.get(roomId).filterNotNull().first().encrypted) return null
        return Result.success(content)
    }

    override suspend fun decrypt(event: ClientEvent.RoomEvent.MessageEvent<*>): Result<MessageEventContent>? {
        if (event.content is RedactedEventContent) return Result.success(event.content)
        if (roomStore.get(event.roomId).first() == null)
            log.warn { "could not find ${event.roomId} in local data, waiting started" }
        if (roomStore.get(event.roomId).filterNotNull().first().encrypted) return null
        return Result.success(event.content)
    }
}