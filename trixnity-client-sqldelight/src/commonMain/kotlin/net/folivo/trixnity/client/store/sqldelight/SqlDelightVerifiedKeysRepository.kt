package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import kotlin.coroutines.CoroutineContext

class SqlDelightVerifiedKeysRepository(
    private val db: DeviceKeysQueries,
    private val context: CoroutineContext
) : VerifiedKeysRepository {
    override suspend fun get(key: VerifiedKeysRepositoryKey): String? = withContext(context) {
        db.getVerifiedKey(
            user_id = key.userId.full,
            device_id = key.deviceId,
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        ).executeAsOneOrNull()
    }

    override suspend fun save(key: VerifiedKeysRepositoryKey, value: String) = withContext(context) {
        db.saveVerifiedKey(
            Sql_verified_keys(
                user_id = key.userId.full,
                device_id = key.deviceId,
                key_id = key.keyId,
                key_algorithm = key.keyAlgorithm.name,
                key_value = value
            )
        )
    }

    override suspend fun delete(key: VerifiedKeysRepositoryKey) = withContext(context) {
        db.deleteVerifiedKey(
            user_id = key.userId.full,
            device_id = key.deviceId,
            key_id = key.keyId,
            key_algorithm = key.keyAlgorithm.name
        )
    }
}