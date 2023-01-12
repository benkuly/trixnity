package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository

internal class RealmOlmForgetFallbackKeyAfter : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var value: Long = 0
}

internal class RealmOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository {
    override suspend fun get(key: Long): Instant? = withRealmRead {
        findByKey(key).find()?.value?.let { Instant.fromEpochMilliseconds(it) }
    }

    override suspend fun save(key: Long, value: Instant) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmOlmForgetFallbackKeyAfter().apply {
            id = key
        }).apply {
            this.value = value.toEpochMilliseconds()
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun delete(key: Long) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmOlmForgetFallbackKeyAfter>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Long) = query<RealmOlmForgetFallbackKeyAfter>("id == $0", key).first()
}