package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.events.Event
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomStateRepository(
    private val db: RoomStateQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomStateRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun getBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? =
        withContext(context) {
            db.getRoomStateByStateKey(firstKey.roomId.full, firstKey.type, secondKey).executeAsOneOrNull()?.let {
                json.decodeFromString(serializer, it)
            }
        }

    override suspend fun saveBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>) =
        withContext(context) {
            db.saveRoomState(firstKey.roomId.full, firstKey.type, secondKey, json.encodeToString(serializer, value))
        }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> = withContext(context) {
        db.getRoomState(key.roomId.full, key.type).executeAsList().associate {
            it.state_key to json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>) = withContext(context) {
        value.forEach { saveBySecondKey(key, it.key, it.value) }
    }

    override suspend fun delete(key: RoomStateRepositoryKey) = withContext(context) {
        db.deleteRoomState(key.roomId.full, key.type)
    }
}