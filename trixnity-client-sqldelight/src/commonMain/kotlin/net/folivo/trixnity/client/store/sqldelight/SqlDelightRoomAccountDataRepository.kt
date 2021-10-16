package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.events.Event.AccountDataEvent
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomAccountDataRepository(
    private val db: RoomAccountDataQueries,
    private val json: Json,
    private val context: CoroutineContext,
) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(AccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomAccountDataRepositoryKey): AccountDataEvent<*>? = withContext(context) {
        db.getRoomAccountData(key.roomId.full, key.type).executeAsOneOrNull()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: RoomAccountDataRepositoryKey, value: AccountDataEvent<*>) = withContext(context) {
        db.saveRoomAccountData(
            key.roomId.full,
            key.type,
            json.encodeToString(serializer, value),
        )
    }

    override suspend fun delete(key: RoomAccountDataRepositoryKey) = withContext(context) {
        db.deleteRoomAccountData(key.roomId.full, key.type)
    }

}