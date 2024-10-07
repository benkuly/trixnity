package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedCrossSigningKeys : Table("cross_signing_keys") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedCrossSigningKeysRepository(private val json: Json) : CrossSigningKeysRepository {
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? = withExposedRead {
        ExposedCrossSigningKeys.selectAll().where { ExposedCrossSigningKeys.userId eq key.full }.firstOrNull()?.let {
            it[ExposedCrossSigningKeys.value].let { deviceKeys ->
                json.decodeFromString<Set<StoredCrossSigningKeys>>(deviceKeys)
            }
        }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>): Unit = withExposedWrite {
        ExposedCrossSigningKeys.upsert {
            it[userId] = key.full
            it[ExposedCrossSigningKeys.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: UserId): Unit = withExposedWrite {
        ExposedCrossSigningKeys.deleteWhere { userId eq key.full }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedCrossSigningKeys.deleteAll()
    }
}