package net.folivo.trixnity.client.store.realm

import io.ktor.util.*
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.repository.MediaRepository

internal class RealmMedia : RealmObject {
    @PrimaryKey
    var url = ""
    var media: String = "" // encode ByteArray as String
}

internal class RealmMediaRepository(
    private val realm: Realm,
) : MediaRepository {
    override suspend fun get(key: String): ByteArray? {
        return realm.findByKey(key).find()?.media?.decodeBase64Bytes()
    }

    override suspend fun changeUri(oldUri: String, newUri: String) {
        realm.write {
            findByKey(oldUri).find()?.apply {
                url = newUri
            }
        }
    }

    override suspend fun save(key: String, value: ByteArray) {
        realm.write {
            val existing = findByKey(key).find()
            val upsert = (existing ?: RealmMedia().apply { url = key }).apply {
                media = value.encodeBase64()
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
            val existing = query<RealmMedia>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKey(key: String) = query<RealmMedia>("url == $0", key).first()
    private fun MutableRealm.findByKey(key: String) = query<RealmMedia>("url == $0", key).first()

}