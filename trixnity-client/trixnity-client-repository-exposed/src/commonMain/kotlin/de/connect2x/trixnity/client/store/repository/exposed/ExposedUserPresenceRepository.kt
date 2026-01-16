package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedUserPresence : Table("user_presence") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedUserPresenceRepository(private val json: Json) : UserPresenceRepository {
    override suspend fun get(key: UserId): UserPresence? = withExposedRead {
        ExposedUserPresence.selectAll().where { ExposedUserPresence.userId eq key.full }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedUserPresence.value])
        }
    }

    override suspend fun save(
        key: UserId,
        value: UserPresence
    ) = withExposedWrite {
        ExposedUserPresence.upsert {
            it[userId] = key.full
            it[ExposedUserPresence.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: UserId): Unit = withExposedWrite {
        ExposedUserPresence.deleteWhere { userId eq key.full }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedUserPresence.deleteAll()
    }
}