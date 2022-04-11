package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository
import org.jetbrains.exposed.sql.*

internal object ExposedSecretKeyRequest : Table("secret_key_request") {
    val id = varchar("id", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

class ExposedSecretKeyRequestRepository(private val json: Json) : SecretKeyRequestRepository {
    override suspend fun getAll(): List<StoredSecretKeyRequest> {
        return ExposedSecretKeyRequest.selectAll()
            .map { json.decodeFromString(it[ExposedSecretKeyRequest.value]) }
    }

    override suspend fun get(key: String): StoredSecretKeyRequest? {
        return ExposedSecretKeyRequest.select { ExposedSecretKeyRequest.id eq key }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedSecretKeyRequest.value])
        }
    }

    override suspend fun save(key: String, value: StoredSecretKeyRequest) {
        ExposedSecretKeyRequest.replace {
            it[this.id] = key
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: String) {
        ExposedSecretKeyRequest.deleteWhere { ExposedSecretKeyRequest.id eq key }
    }

    override suspend fun deleteAll() {
        ExposedSecretKeyRequest.deleteAll()
    }
}