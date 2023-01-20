package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.client.store.sqldelight.KeysQueries
import net.folivo.trixnity.client.store.sqldelight.Sql_key_verification_state
import kotlin.coroutines.CoroutineContext

class SqlDelightKeyVerificationStateRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : KeyVerificationStateRepository {
    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? = withContext(context) {
        db.getKeyVerificationState(
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        ).executeAsOneOrNull()?.let { json.decodeFromString(it) }
    }

    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState) = withContext(context) {
        db.saveKeyVerificationState(
            Sql_key_verification_state(
                key_id = key.keyId,
                key_algorithm = key.keyAlgorithm.name,
                verification_state = json.encodeToString(value)
            )
        )
    }

    override suspend fun delete(key: KeyVerificationStateKey) = withContext(context) {
        db.deleteKeyVerificationState(
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        )
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllKeyVerificationStates()
    }
}