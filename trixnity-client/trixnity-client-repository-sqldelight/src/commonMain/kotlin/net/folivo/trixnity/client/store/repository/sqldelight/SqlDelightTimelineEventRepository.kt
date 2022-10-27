package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.repository.TimelineEventKey
import net.folivo.trixnity.client.store.repository.TimelineEventRepository
import net.folivo.trixnity.client.store.sqldelight.RoomTimelineQueries
import kotlin.coroutines.CoroutineContext

class SqlDelightTimelineEventRepository(
    private val db: RoomTimelineQueries,
    private val json: Json,
    private val context: CoroutineContext
) : TimelineEventRepository {
    override suspend fun get(key: TimelineEventKey): TimelineEvent? = withContext(context) {
        db.getTimelineEvent(key.eventId.full, key.roomId.full).executeAsOneOrNull()?.let {
            json.decodeFromString(it)
        }
    }

    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) = withContext(context) {
        db.saveTimelineEvent(key.eventId.full, key.roomId.full, json.encodeToString(value))
    }

    override suspend fun delete(key: TimelineEventKey) = withContext(context) {
        db.deleteTimelineEvent(key.eventId.full, key.roomId.full)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllTimelineEvents()
    }
}