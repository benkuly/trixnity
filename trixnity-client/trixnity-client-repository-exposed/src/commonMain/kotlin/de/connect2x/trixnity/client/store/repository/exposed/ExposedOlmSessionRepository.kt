package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedOlmSession : Table("olm_session") {
    val senderKey = varchar("sender_key", length = 255)
    override val primaryKey = PrimaryKey(senderKey)
    val value = text("value")
}

internal class ExposedOlmSessionRepository(private val json: Json) : OlmSessionRepository {
    override suspend fun get(key: Curve25519KeyValue): Set<StoredOlmSession>? = withExposedRead {
        ExposedOlmSession.selectAll().where { ExposedOlmSession.senderKey eq key.value }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedOlmSession.value]) }
    }

    override suspend fun getAll(): List<Set<StoredOlmSession>> = withExposedRead {
        ExposedOlmSession.selectAll().map { json.decodeFromString(it[ExposedOlmSession.value]) }
    }

    override suspend fun save(key: Curve25519KeyValue, value: Set<StoredOlmSession>): Unit = withExposedWrite {
        ExposedOlmSession.upsert {
            it[senderKey] = key.value
            it[ExposedOlmSession.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Curve25519KeyValue): Unit = withExposedWrite {
        ExposedOlmSession.deleteWhere { senderKey eq key.value }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedOlmSession.deleteAll()
    }
}