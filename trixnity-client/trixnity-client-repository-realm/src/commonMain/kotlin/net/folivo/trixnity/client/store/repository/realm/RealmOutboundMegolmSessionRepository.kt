package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

internal class RealmOutboundMegolmSession : RealmObject {
    @PrimaryKey
    var roomId: String = ""
    var value: String = ""
}

internal class RealmOutboundMegolmSessionRepository(
    private val json: Json,
) : OutboundMegolmSessionRepository {
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? = withRealmRead {
        findByKey(key).find()?.let {
            json.decodeFromString(it.value)
        }
    }

    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmOutboundMegolmSession().apply { roomId = key.full }).apply {
            this.value = json.encodeToString(value)
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun delete(key: RoomId) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmOutboundMegolmSession>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: RoomId) =
        query<RealmOutboundMegolmSession>("roomId == $0", key.full).first()
}