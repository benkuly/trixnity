package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.RoomAccountDataQueries
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomAccountDataRepository(
    private val db: RoomAccountDataQueries,
    private val json: Json,
    private val context: CoroutineContext,
) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomAccountDataRepositoryKey): Map<String, RoomAccountDataEvent<*>>? =
        withContext(context) {
            db.getRoomAccountData(key.roomId.full, key.type).executeAsList().associate {
                it.key to json.decodeFromString(serializer, it.event)
            }
        }

    override suspend fun save(key: RoomAccountDataRepositoryKey, value: Map<String, RoomAccountDataEvent<*>>) =
        withContext(context) {
            value.forEach { saveBySecondKey(key, it.key, it.value) }
        }

    override suspend fun delete(key: RoomAccountDataRepositoryKey) = withContext(context) {
        db.deleteRoomAccountData(key.roomId.full, key.type)
    }

    override suspend fun getBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ): RoomAccountDataEvent<*>? = withContext(context) {
        db.getRoomAccountDataByKey(firstKey.roomId.full, firstKey.type, secondKey).executeAsOneOrNull()
            ?.let { json.decodeFromString(serializer, it.event) }
    }

    override suspend fun saveBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: RoomAccountDataEvent<*>
    ) = withContext(context) {
        db.saveRoomAccountData(firstKey.roomId.full, firstKey.type, secondKey, json.encodeToString(serializer, value))
    }

    override suspend fun deleteBySecondKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String) =
        withContext(context) {
            db.deleteRoomAccountDataByKey(firstKey.roomId.full, firstKey.type, secondKey)
        }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllRoomAccountData()
    }
}