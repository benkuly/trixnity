package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository

internal class RealmMediaCacheMapping : RealmObject {
    @PrimaryKey
    var cacheUri: String = ""
    var mxcUri: String? = null
    var size: Long = 0
    var contentType: String? = null
}

internal class RealmMediaCacheMappingRepository : MediaCacheMappingRepository {
    override suspend fun get(key: String): MediaCacheMapping? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            MediaCacheMapping(
                cacheUri = it.cacheUri,
                mxcUri = it.mxcUri,
                size = it.size,
                contentType = it.contentType
            )
        }
    }

    override suspend fun save(key: String, value: MediaCacheMapping): Unit = withRealmWrite {
        copyToRealm(
            RealmMediaCacheMapping().apply {
                cacheUri = key
                mxcUri = value.mxcUri
                size = value.size
                contentType = value.contentType
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: String) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmMediaCacheMapping>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: String) = query<RealmMediaCacheMapping>("cacheUri == $0", key).first()
}
