package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredCrossSigningKey
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedCrossSigningKeys : Table("cross_signing_keys") {
    val userId = varchar("user_id", length = 65535)
    override val primaryKey = PrimaryKey(userId)
    val crossSigningKeys = text("cross_signing_keys")
}

internal class ExposedCrossSigningKeysRepository(private val json: Json) : CrossSigningKeysRepository {
    override suspend fun get(key: UserId): Set<StoredCrossSigningKey>? {
        return ExposedCrossSigningKeys.select { ExposedCrossSigningKeys.userId eq key.full }.firstOrNull()?.let {
            it[ExposedCrossSigningKeys.crossSigningKeys].let { deviceKeys ->
                json.decodeFromString<Set<StoredCrossSigningKey>>(deviceKeys)
            }
        }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKey>) {
        ExposedCrossSigningKeys.replace {
            it[userId] = key.full
            it[crossSigningKeys] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: UserId) {
        ExposedCrossSigningKeys.deleteWhere { ExposedCrossSigningKeys.userId eq key.full }
    }
}