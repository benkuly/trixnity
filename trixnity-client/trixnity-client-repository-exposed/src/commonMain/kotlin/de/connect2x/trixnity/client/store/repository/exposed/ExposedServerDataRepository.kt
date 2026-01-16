package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.repository.ServerDataRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedServerData : LongIdTable("server_data") {
    val value = text("value")
}

internal class ExposedServerDataRepository(private val json: Json) : ServerDataRepository {
    override suspend fun get(key: Long): ServerData? = withExposedRead {
        ExposedServerData.selectAll().where { ExposedServerData.id eq key }.firstOrNull()?.let {
            it[ExposedServerData.value].let { outdated -> json.decodeFromString<ServerData>(outdated) }
        }
    }

    override suspend fun save(key: Long, value: ServerData): Unit = withExposedWrite {
        ExposedServerData.upsert {
            it[id] = key
            it[ExposedServerData.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedServerData.deleteWhere { ExposedServerData.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedServerData.deleteAll()
    }
}