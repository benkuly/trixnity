package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.ServerVersions
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.upsert

internal object ExposedServerVersions : LongIdTable("server_versions") {
    val value = text("value")
}

internal class ExposedServerVersionsRepository(private val json: Json) : ServerVersionsRepository {
    override suspend fun get(key: Long): ServerVersions? = withExposedRead {
        ExposedServerVersions.select { ExposedServerVersions.id eq key }.firstOrNull()?.let {
            it[ExposedServerVersions.value].let { outdated -> json.decodeFromString<ServerVersions>(outdated) }
        }
    }

    override suspend fun save(key: Long, value: ServerVersions): Unit = withExposedWrite {
        ExposedServerVersions.upsert {
            it[id] = key
            it[ExposedServerVersions.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedServerVersions.deleteWhere { ExposedServerVersions.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedServerVersions.deleteAll()
    }
}