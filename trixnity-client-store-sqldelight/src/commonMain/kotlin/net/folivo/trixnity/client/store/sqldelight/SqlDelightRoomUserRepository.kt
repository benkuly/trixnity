package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomUserRepository(
    private val db: RoomUserQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomUserRepository {
    override suspend fun getBySecondKey(firstKey: RoomId, secondKey: UserId): RoomUser? = withContext(context) {
        db.getRoomUser(firstKey.full, secondKey.full).executeAsOneOrNull()?.let {
            json.decodeFromString(it)
        }
    }

    override suspend fun saveBySecondKey(firstKey: RoomId, secondKey: UserId, roomUser: RoomUser) =
        withContext(context) {
            db.saveRoomUser(firstKey.full, secondKey.full, json.encodeToString(roomUser))
        }

    override suspend fun deleteBySecondKey(firstKey: RoomId, secondKey: UserId) = withContext(context) {
        db.deleteRoomUser(firstKey.full, secondKey.full)
    }

    override suspend fun get(key: RoomId): Map<UserId, RoomUser> = withContext(context) {
        db.getRoomUsers(key.full).executeAsList().map {
            json.decodeFromString<RoomUser>(it.room_user)
        }.associateBy { it.userId }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) = withContext(context) {
        value.values.forEach { saveBySecondKey(key, it.userId, it) }
    }

    override suspend fun delete(key: RoomId) = withContext(context) {
        db.deleteRoomUsers(key.full)
    }
}