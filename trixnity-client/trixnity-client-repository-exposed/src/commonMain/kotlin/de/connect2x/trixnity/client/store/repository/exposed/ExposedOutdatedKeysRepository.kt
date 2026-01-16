package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.core.model.UserId
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedOutdatedKeys : LongIdTable("outdated_keys") {
    val value = text("value")
}

internal class ExposedOutdatedKeysRepository(private val json: Json) : OutdatedKeysRepository {
    override suspend fun get(key: Long): Set<UserId>? = withExposedRead {
        ExposedOutdatedKeys.selectAll().where { ExposedOutdatedKeys.id eq key }.firstOrNull()?.let {
            it[ExposedOutdatedKeys.value].let { outdated -> json.decodeFromString<Set<UserId>>(outdated) }
        }
    }

    override suspend fun save(key: Long, value: Set<UserId>): Unit = withExposedWrite {
        ExposedOutdatedKeys.upsert {
            it[id] = key
            it[ExposedOutdatedKeys.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedOutdatedKeys.deleteWhere { ExposedOutdatedKeys.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedOutdatedKeys.deleteAll()
    }
}