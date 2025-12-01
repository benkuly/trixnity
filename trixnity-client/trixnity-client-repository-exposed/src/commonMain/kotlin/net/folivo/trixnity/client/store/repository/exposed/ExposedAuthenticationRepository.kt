package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Authentication
import net.folivo.trixnity.client.store.repository.AuthenticationRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedAuthentication : LongIdTable("authentication") {
    val value = text("value").nullable()
}

internal class ExposedAuthenticationRepository(private val json: Json) : AuthenticationRepository {
    override suspend fun get(key: Long): Authentication? = withExposedRead {
        ExposedAuthentication.selectAll().where { ExposedAuthentication.id eq key }.firstOrNull()
            ?.get(ExposedAuthentication.value)
            ?.let { json.decodeFromString(it) }
    }

    override suspend fun save(key: Long, value: Authentication): Unit = withExposedWrite {
        ExposedAuthentication.upsert {
            it[id] = key
            it[ExposedAuthentication.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedAuthentication.deleteWhere { ExposedAuthentication.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedAuthentication.deleteAll()
    }
}

