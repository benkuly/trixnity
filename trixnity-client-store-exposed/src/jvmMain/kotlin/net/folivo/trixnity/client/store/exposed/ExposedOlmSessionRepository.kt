package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredOlmSession
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.crypto.Key
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedOlmSession : Table("olm_session") {
    val senderKey = varchar("sender_key", length = 65535)
    override val primaryKey = PrimaryKey(senderKey)
    val pickled = text("pickled")
}

internal class ExposedOlmSessionRepository(private val json: Json) : OlmSessionRepository {
    override suspend fun get(key: Key.Curve25519Key): Set<StoredOlmSession>? {
        return ExposedOlmSession.select { ExposedOlmSession.senderKey eq key.value }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedOlmSession.pickled]) }
    }

    override suspend fun save(key: Key.Curve25519Key, value: Set<StoredOlmSession>) {
        ExposedOlmSession.replace {
            it[senderKey] = key.value
            it[pickled] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Key.Curve25519Key) {
        ExposedOlmSession.deleteWhere { ExposedOlmSession.senderKey eq key.value }
    }
}