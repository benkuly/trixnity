package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.verification.KeyVerificationState

internal class RealmKeyVerificationState : RealmObject {
    var keyId: String = ""
    var keyAlgorithm: String = ""
    var verificationState: String = ""
}

internal class RealmKeyVerificationStateRepository(
    private val realm: Realm,
    private val json: Json,
) : KeyVerificationStateRepository {
    override suspend fun get(key: VerifiedKeysRepositoryKey): KeyVerificationState? {
        return realm.findByKey(key).find()?.let {
            json.decodeFromString(it.verificationState)
        }
    }

    override suspend fun save(key: VerifiedKeysRepositoryKey, value: KeyVerificationState) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmKeyVerificationState()).apply {
                keyId = key.keyId
                keyAlgorithm = key.keyAlgorithm.name
                verificationState = json.encodeToString(value)
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: VerifiedKeysRepositoryKey) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmKeyVerificationState>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: VerifiedKeysRepositoryKey) =
        query<RealmKeyVerificationState>(
            "keyId == $0 && keyAlgorithm = $1",
            key.keyId,
            key.keyAlgorithm.name
        ).first()

    private fun MutableRealm.findByKey(key: VerifiedKeysRepositoryKey) =
        query<RealmKeyVerificationState>(
            "keyId == $0 && keyAlgorithm = $1",
            key.keyId,
            key.keyAlgorithm.name
        ).first()

}