package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.verification.KeyVerificationState
import kotlin.coroutines.CoroutineContext

class SqlDelightKeyVerificationStateRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : KeyVerificationStateRepository {
    override suspend fun get(key: VerifiedKeysRepositoryKey): KeyVerificationState? = withContext(context) {
        db.getKeyVerificationState(
            user_id = key.userId.full,
            device_id = key.deviceId,
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        ).executeAsOneOrNull()?.let { json.decodeFromString(it) }
    }

    override suspend fun save(key: VerifiedKeysRepositoryKey, value: KeyVerificationState) = withContext(context) {
        db.saveKeyVerificationState(
            Sql_key_verification_state(
                user_id = key.userId.full,
                device_id = key.deviceId,
                key_id = key.keyId,
                key_algorithm = key.keyAlgorithm.name,
                verification_state = json.encodeToString(value)
            )
        )
    }

    override suspend fun delete(key: VerifiedKeysRepositoryKey) = withContext(context) {
        db.deleteKeyVerificationState(
            user_id = key.userId.full,
            device_id = key.deviceId,
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        )
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllKeyVerificationStates()
    }
}