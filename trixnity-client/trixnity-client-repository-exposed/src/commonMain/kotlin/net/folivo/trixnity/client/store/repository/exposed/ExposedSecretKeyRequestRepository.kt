package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedSecretKeyRequest : Table("secret_key_request") {
    val id = varchar("id", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

class ExposedSecretKeyRequestRepository(private val json: Json) : SecretKeyRequestRepository {
    override suspend fun getAll(): List<StoredSecretKeyRequest> = withExposedRead {
        ExposedSecretKeyRequest.selectAll()
            .map { json.decodeFromString(it[ExposedSecretKeyRequest.value]) }
    }

    override suspend fun get(key: String): StoredSecretKeyRequest? = withExposedRead {
        ExposedSecretKeyRequest.selectAll().where { ExposedSecretKeyRequest.id eq key }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedSecretKeyRequest.value])
        }
    }

    override suspend fun save(key: String, value: StoredSecretKeyRequest): Unit = withExposedWrite {
        ExposedSecretKeyRequest.upsert {
            it[id] = key
            it[ExposedSecretKeyRequest.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: String): Unit = withExposedWrite {
        ExposedSecretKeyRequest.deleteWhere { id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedSecretKeyRequest.deleteAll()
    }
}