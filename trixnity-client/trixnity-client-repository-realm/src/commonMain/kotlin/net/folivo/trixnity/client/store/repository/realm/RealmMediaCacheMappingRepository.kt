package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository

internal class RealmMediaCacheMapping : RealmObject {
    @PrimaryKey
    var cacheUri: String = ""
    var mxcUri: String? = null
    var contentType: String? = null
}

internal class RealmMediaCacheMappingRepository : MediaCacheMappingRepository {
    override suspend fun get(key: String): MediaCacheMapping? = withRealmRead {
        findByKey(key).find()?.let {
            MediaCacheMapping(
                cacheUri = it.cacheUri,
                mxcUri = it.mxcUri,
                contentType = it.contentType
            )
        }
    }

    override suspend fun save(key: String, value: MediaCacheMapping) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmMediaCacheMapping().apply { cacheUri = value.cacheUri }).apply {
            mxcUri = value.mxcUri
            contentType = value.contentType
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun delete(key: String) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmMediaCacheMapping>().find()
        delete(existing)
    }

    private fun Realm.findByKey(key: String) = query<RealmMediaCacheMapping>("cacheUri == $0", key).first()
    private fun MutableRealm.findByKey(key: String) = query<RealmMediaCacheMapping>("cacheUri == $0", key).first()
}
