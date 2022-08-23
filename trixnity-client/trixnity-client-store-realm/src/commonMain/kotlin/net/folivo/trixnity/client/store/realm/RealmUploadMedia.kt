package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.client.store.repository.UploadMediaRepository

internal class RealmUploadMedia : RealmObject {
    @PrimaryKey
    var cacheUri: String = ""
    var mxcUri: String? = null
    var contentType: String? = null
}

internal class RealmUploadMediaRepository(
    private val realm: Realm,
) : UploadMediaRepository {
    override suspend fun get(key: String): UploadCache? {
        return realm.findByKey(key).find()?.let {
            UploadCache(
                cacheUri = it.cacheUri,
                mxcUri = it.mxcUri,
                contentType = it.contentType
            )
        }
    }

    override suspend fun save(key: String, value: UploadCache) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmUploadMedia().apply { cacheUri = value.cacheUri }).apply {
                mxcUri = value.mxcUri
                contentType = value.contentType
            }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: String) {
        realm.write {
            val existing = findByKey(key)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmUploadMedia>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: String) = query<RealmUploadMedia>("cacheUri == $0", key).first()
    private fun MutableRealm.findByKey(key: String) = query<RealmUploadMedia>("cacheUri == $0", key).first()
}
