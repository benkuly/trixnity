package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event

internal class RealmGlobalAccountData : RealmObject {
    @PrimaryKey
    var id: String = ""

    var type: String = ""
    var key: String = ""
    var event: String = ""
}

internal class RealmGlobalAccountDataRepository(
    private val json: Json
) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: String): Map<String, Event.GlobalAccountDataEvent<*>> = withRealmRead {
        query<RealmGlobalAccountData>("type == $0", firstKey).find().copyFromRealm()
            .associate { realmGlobalAccountData ->
                realmGlobalAccountData.key to json.decodeFromString(serializer, realmGlobalAccountData.event)
            }
    }

    override suspend fun get(firstKey: String, secondKey: String): Event.GlobalAccountDataEvent<*>? =
        withRealmRead {
            findByKeys(firstKey, secondKey).find()?.copyFromRealm()?.let {
                json.decodeFromString(serializer, it.event)
            }
        }

    override suspend fun save(
        firstKey: String,
        secondKey: String,
        value: Event.GlobalAccountDataEvent<*>
    ): Unit =
        withRealmWrite {
            copyToRealm(
                RealmGlobalAccountData()
                    .apply {
                        this.id = serializeKey(firstKey, secondKey)
                        this.type = firstKey
                        this.key = secondKey
                        this.event = json.encodeToString(serializer, value)
                    },
                UpdatePolicy.ALL
            )
        }

    override suspend fun delete(firstKey: String, secondKey: String) = withRealmWrite {
        val existing = findByKeys(firstKey, secondKey)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmGlobalAccountData>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKeys(
        firstKey: String,
        secondKey: String
    ) = query<RealmGlobalAccountData>("type == $0 && key == $1", firstKey, secondKey).first()
}
