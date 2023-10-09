package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent

internal class RealmRoomAccountData : RealmObject {
    @PrimaryKey
    var id: String = ""

    var roomId: String = ""
    var type: String = ""
    var key: String = ""
    var event: String = ""
}

internal class RealmRoomAccountDataRepository(
    private val json: Json,
) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: RoomAccountDataRepositoryKey): Map<String, RoomAccountDataEvent<*>> =
        withRealmRead {
            findByKey(firstKey).copyFromRealm().associate {
                it.key to json.decodeFromString(serializer, it.event)
            }
        }

    override suspend fun deleteByRoomId(roomId: RoomId) = withRealmWrite {
        val existing = query<RealmRoomAccountData>("roomId == $0", roomId.full).find()
        delete(existing)
    }

    override suspend fun get(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ): RoomAccountDataEvent<*>? = withRealmRead {
        findByKeys(firstKey, secondKey).find()?.copyFromRealm()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: RoomAccountDataEvent<*>
    ): Unit = withRealmWrite {
        copyToRealm(
            RealmRoomAccountData().apply {
                this.id = serializeKey(firstKey, secondKey)
                this.roomId = firstKey.roomId.full
                this.type = firstKey.type
                this.key = secondKey
                this.event = json.encodeToString(serializer, value)
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(firstKey: RoomAccountDataRepositoryKey, secondKey: String) = withRealmWrite {
        val existing = findByKeys(firstKey, secondKey)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmRoomAccountData>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: RoomAccountDataRepositoryKey) =
        query<RealmRoomAccountData>("roomId == $0 && type == $1", key.roomId.full, key.type).find()

    private fun TypedRealm.findByKeys(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ) = query<RealmRoomAccountData>(
        "roomId == $0 && type == $1 && key == $2",
        firstKey.roomId.full,
        firstKey.type,
        secondKey
    ).first()
}