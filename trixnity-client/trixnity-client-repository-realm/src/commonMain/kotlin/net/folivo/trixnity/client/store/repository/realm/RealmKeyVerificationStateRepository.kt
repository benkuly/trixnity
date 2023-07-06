package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.KeyVerificationState
import net.folivo.trixnity.client.store.repository.KeyVerificationStateKey
import net.folivo.trixnity.client.store.repository.KeyVerificationStateRepository

internal class RealmKeyVerificationState : RealmObject {
    @PrimaryKey
    var id: String = ""

    var keyId: String = ""
    var keyAlgorithm: String = ""
    var verificationState: String = ""
}

internal class RealmKeyVerificationStateRepository(
    private val json: Json,
) : KeyVerificationStateRepository {
    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.verificationState)
        }
    }

    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState): Unit = withRealmWrite {
        copyToRealm(
            RealmKeyVerificationState().apply {
                id = serializeKey(key)
                keyId = key.keyId
                keyAlgorithm = key.keyAlgorithm.name
                verificationState = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
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