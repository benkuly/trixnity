package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.ServerData
import net.folivo.trixnity.client.store.repository.ServerDataRepository

internal class RealmServerData : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var value: String = ""
}

internal class RealmServerDataRepository(
    private val json: Json,
) : ServerDataRepository {
    override suspend fun get(key: Long): ServerData? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: Long, value: ServerData): Unit = withRealmWrite {
        copyToRealm(
            RealmServerData().apply {
                id = key
                this.value = json.encodeToString(value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: Long) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmServerData>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Long) = query<RealmServerData>("id == $0", key).first()
}