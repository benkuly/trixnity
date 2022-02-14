package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository
import kotlin.coroutines.CoroutineContext

class SqlDelightSecretKeyRequestRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : SecretKeyRequestRepository {
    override suspend fun getAll(): List<StoredSecretKeyRequest> = withContext(context) {
        db.getAllSecretKeyRequests().executeAsList().map {
            json.decodeFromString(it.secret_key_request)
        }
    }

    override suspend fun get(key: String): StoredSecretKeyRequest? = withContext(context) {
        db.getSecretKeyRequest(key).executeAsOneOrNull()?.let {
            json.decodeFromString(it.secret_key_request)
        }
    }

    override suspend fun save(key: String, value: StoredSecretKeyRequest) = withContext(context) {
        db.saveSecretKeyRequest(key, json.encodeToString(value))
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteSecretKeyRequest(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllSecretKeyRequests()
    }
}