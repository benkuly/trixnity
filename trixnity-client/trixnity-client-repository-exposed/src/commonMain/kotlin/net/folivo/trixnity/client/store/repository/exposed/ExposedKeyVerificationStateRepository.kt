package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedKeyVerificationState : Table("key_verification_state") {
    val keyId = varchar("key_id", length = 255)
    val keyAlgorithm = varchar("key_algorithm", length = 255)
    override val primaryKey = PrimaryKey(keyId, keyAlgorithm)
    val verificationState = text("verification_state")
}

internal class ExposedKeyVerificationStateRepository(private val json: Json) : KeyVerificationStateRepository {
    override suspend fun get(key: VerifiedKeysRepositoryKey): KeyVerificationState? = withExposedRead {
        ExposedKeyVerificationState.select {
            ExposedKeyVerificationState.keyId.eq(key.keyId) and
                    ExposedKeyVerificationState.keyAlgorithm.eq(key.keyAlgorithm.name)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedKeyVerificationState.verificationState])
        }
    }

    override suspend fun save(key: VerifiedKeysRepositoryKey, value: KeyVerificationState): Unit = withExposedWrite {
        ExposedKeyVerificationState.replace {
            it[keyId] = key.keyId
            it[keyAlgorithm] = key.keyAlgorithm.name
            it[verificationState] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: VerifiedKeysRepositoryKey): Unit = withExposedWrite {
        ExposedKeyVerificationState.deleteWhere {
            keyId.eq(key.keyId) and
                    keyAlgorithm.eq(key.keyAlgorithm.name)
        }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedKeyVerificationState.deleteAll()
    }
}