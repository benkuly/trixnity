package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedOlmSession : Table("olm_session") {
    val senderKey = varchar("sender_key", length = 255)
    override val primaryKey = PrimaryKey(senderKey)
    val value = text("value")
}

internal class ExposedOlmSessionRepository(private val json: Json) : OlmSessionRepository {
    override suspend fun get(key: Key.Curve25519Key): Set<StoredOlmSession>? = withExposedRead {
        ExposedOlmSession.select { ExposedOlmSession.senderKey eq key.value }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedOlmSession.value]) }
    }

    override suspend fun save(key: Key.Curve25519Key, value: Set<StoredOlmSession>): Unit = withExposedWrite {
        ExposedOlmSession.replace {
            it[senderKey] = key.value
            it[ExposedOlmSession.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Key.Curve25519Key): Unit = withExposedWrite {
        ExposedOlmSession.deleteWhere { senderKey eq key.value }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedOlmSession.deleteAll()
    }
}