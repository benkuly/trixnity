package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedOutdatedKeys : LongIdTable("outdated_keys") {
    val outdatedKeys = text("outdated_keys")
}

internal class ExposedOutdatedKeysRepository(private val json: Json) : OutdatedKeysRepository {
    override suspend fun get(key: Long): Set<UserId>? {
        return ExposedOutdatedKeys.select { ExposedOutdatedKeys.id eq key }.firstOrNull()?.let {
            it[ExposedOutdatedKeys.outdatedKeys].let { outdated -> json.decodeFromString<Set<UserId>>(outdated) }
        }
    }

    override suspend fun save(key: Long, value: Set<UserId>) {
        ExposedOutdatedKeys.replace {
            it[id] = key
            it[outdatedKeys] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long) {
        ExposedOutdatedKeys.deleteWhere { ExposedOutdatedKeys.id eq key }
    }
}