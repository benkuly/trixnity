package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
import net.folivo.trixnity.client.store.repository.RoomKeyRequestRepository
import net.folivo.trixnity.client.store.sqldelight.KeysQueries
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomKeyRequestRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomKeyRequestRepository {
    override suspend fun getAll(): List<StoredRoomKeyRequest> = withContext(context) {
        db.getAllRoomKeyRequests().executeAsList().map {
            json.decodeFromString(it.room_key_request)
        }
    }

    override suspend fun get(key: String): StoredRoomKeyRequest? = withContext(context) {
        db.getRoomKeyRequest(key).executeAsOneOrNull()?.let {
            json.decodeFromString(it.room_key_request)
        }
    }

    override suspend fun save(key: String, value: StoredRoomKeyRequest) = withContext(context) {
        db.saveRoomKeyRequest(key, json.encodeToString(value))
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteRoomKeyRequest(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllRoomKeyRequests()
    }
}