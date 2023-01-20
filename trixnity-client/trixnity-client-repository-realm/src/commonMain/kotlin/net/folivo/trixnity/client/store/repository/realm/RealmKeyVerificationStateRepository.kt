package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey

internal class RealmKeyVerificationState : RealmObject {
    var keyId: String = ""
    var keyAlgorithm: String = ""
    var verificationState: String = ""
}

internal class RealmKeyVerificationStateRepository(
    private val json: Json,
) : KeyVerificationStateRepository {
    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? = withRealmRead {
        findByKey(key).find()?.let {
            json.decodeFromString(it.verificationState)
        }
    }

    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState) = withRealmWrite {
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

    override suspend fun delete(key: KeyVerificationStateKey) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmKeyVerificationState>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: KeyVerificationStateKey) =
        query<RealmKeyVerificationState>(
            "keyId == $0 && keyAlgorithm = $1",
            key.keyId,
            key.keyAlgorithm.name
        ).first()
}