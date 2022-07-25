package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.crypto.SecretType
import kotlin.coroutines.CoroutineContext

class SqlDelightSecretsRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : SecretsRepository {
    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? = withContext(context) {
        db.getSecrets(key).executeAsOneOrNull()
            ?.let {
                it.secrets
                    ?.let { secrets -> json.decodeFromString(secrets) }
            }
    }

    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) = withContext(context) {
        db.saveSecrets(Sql_secrets(key, json.encodeToString(value)))
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteSecrets(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllSecrets()
    }
}