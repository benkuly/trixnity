package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.StoredRoomKeyRequest
import de.connect2x.trixnity.client.store.repository.RoomKeyRequestRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomKeyRequest : Table("room_key_request") {
    val id = varchar("id", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

class ExposedRoomKeyRequestRepository(private val json: Json) : RoomKeyRequestRepository {
    override suspend fun getAll(): List<StoredRoomKeyRequest> = withExposedRead {
        ExposedRoomKeyRequest.selectAll()
            .map { json.decodeFromString(it[ExposedRoomKeyRequest.value]) }
    }

    override suspend fun get(key: String): StoredRoomKeyRequest? = withExposedRead {
        ExposedRoomKeyRequest.selectAll().where { ExposedRoomKeyRequest.id eq key }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedRoomKeyRequest.value])
        }
    }

    override suspend fun save(key: String, value: StoredRoomKeyRequest): Unit = withExposedWrite {
        ExposedRoomKeyRequest.upsert {
            it[id] = key
            it[ExposedRoomKeyRequest.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: String): Unit = withExposedWrite {
        ExposedRoomKeyRequest.deleteWhere { id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomKeyRequest.deleteAll()
    }
}