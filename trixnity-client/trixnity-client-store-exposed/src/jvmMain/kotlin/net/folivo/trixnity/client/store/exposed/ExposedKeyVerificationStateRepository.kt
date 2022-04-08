package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.verification.KeyVerificationState
import org.jetbrains.exposed.sql.*

internal object ExposedKeyVerificationState : Table("key_verification_state") {
    val userId = varchar("user_id", length = 16383)
    val deviceId = varchar("device_id", length = 16383)
    val keyId = varchar("key_id", length = 16383)
    val keyAlgorithm = varchar("key_algorithm", length = 16383)
    override val primaryKey = PrimaryKey(userId, deviceId, keyId, keyAlgorithm)
    val verificationState = text("verification_state")
}

internal class ExposedKeyVerificationStateRepository(private val json: Json) : KeyVerificationStateRepository {
    override suspend fun get(key: VerifiedKeysRepositoryKey): KeyVerificationState? {
        return ExposedKeyVerificationState.select {
            ExposedKeyVerificationState.userId.eq(key.userId.full) and
                    ExposedKeyVerificationState.deviceId.eq(key.deviceId ?: "") and
                    ExposedKeyVerificationState.keyId.eq(key.keyId) and
                    ExposedKeyVerificationState.keyAlgorithm.eq(key.keyAlgorithm.name)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedKeyVerificationState.verificationState])
        }
    }

    override suspend fun save(key: VerifiedKeysRepositoryKey, value: KeyVerificationState) {
        ExposedKeyVerificationState.replace {
            it[userId] = key.userId.full
            it[deviceId] = key.deviceId ?: ""
            it[keyId] = key.keyId
            it[keyAlgorithm] = key.keyAlgorithm.name
            it[verificationState] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: VerifiedKeysRepositoryKey) {
        ExposedKeyVerificationState.deleteWhere {
            ExposedKeyVerificationState.userId.eq(key.userId.full) and
                    ExposedKeyVerificationState.deviceId.eq(key.deviceId ?: "") and
                    ExposedKeyVerificationState.keyId.eq(key.keyId) and
                    ExposedKeyVerificationState.keyAlgorithm.eq(key.keyAlgorithm.name)
        }
    }

    override suspend fun deleteAll() {
        ExposedKeyVerificationState.deleteAll()
    }
}