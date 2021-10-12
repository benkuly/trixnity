package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.RoomTimelineRepository
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomTimelineRepository(
    private val db: RoomTimelineQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomTimelineRepository {
    override suspend fun get(key: RoomTimelineKey): TimelineEvent? = withContext(context) {
        db.getTimelineEvent(key.eventId.full, key.roomId.full).executeAsOneOrNull()?.let {
            json.decodeFromString(it)
        }
    }

    override suspend fun save(key: RoomTimelineKey, value: TimelineEvent) = withContext(context) {
        db.saveTimelineEvent(key.eventId.full, key.roomId.full, json.encodeToString(value))
    }

    override suspend fun delete(key: RoomTimelineKey) = withContext(context) {
        db.deleteTimelineEvent(key.eventId.full, key.roomId.full)
    }

}