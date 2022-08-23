package net.folivo.trixnity.client.store.realm

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event

internal class RealmGlobalAccountData : RealmObject {
    var type: String = ""
    var key: String = "" // TODO when RealmDictionaries are added to the KotlinSDK we could use those
    var event: String = ""
}

internal class RealmGlobalAccountDataRepository(
    private val realm: Realm,
    private val json: Json
) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: String): Map<String, Event.GlobalAccountDataEvent<*>> {
        return realm.query<RealmGlobalAccountData>("type == $0", key).find().associate { realmGlobalAccountData ->
            realmGlobalAccountData.key to json.decodeFromString(serializer, realmGlobalAccountData.event)
        }
    }

    override suspend fun getBySecondKey(firstKey: String, secondKey: String): Event.GlobalAccountDataEvent<*>? {
        return realm.findByKeys(firstKey, secondKey).find()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: String, value: Map<String, Event.GlobalAccountDataEvent<*>>) {
        realm.write {
            value.entries.forEach { (secondKey, event) ->
                val existing = findByKeys(key, secondKey).find()
                val upsert = (existing ?: RealmGlobalAccountData())
                    .apply {
                        this.type = key
                        this.key = secondKey
                        this.event = json.encodeToString(serializer, event)
                    }
                if (existing == null) {
                    copyToRealm(upsert)
                }
            }
        }
    }

    override suspend fun saveBySecondKey(firstKey: String, secondKey: String, value: Event.GlobalAccountDataEvent<*>) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey).find()
            val upsert = (existing ?: RealmGlobalAccountData())
                .apply {
                    this.type = firstKey
                    this.key = secondKey
                    this.event = json.encodeToString(serializer, value)
                }
            if (existing == null) {
                copyToRealm(upsert)
            }
        }
    }

    override suspend fun delete(key: String) {
        realm.write {
            val existing = query<RealmGlobalAccountData>("type == $0", key).find()
            delete(existing)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: String, secondKey: String) {
        realm.write {
            val existing = findByKeys(firstKey, secondKey)
            delete(existing)
        }
    }

    override suspend fun deleteAll() {
        realm.write {
            val existing = query<RealmGlobalAccountData>().find()
            delete(existing)
        }
    }

    private fun Realm.findByKeys(
        firstKey: String,
        secondKey: String
    ) = query<RealmGlobalAccountData>("type == $0 && key == $1", firstKey, secondKey).first()

    private fun MutableRealm.findByKeys(
        firstKey: String,
        secondKey: String
    ) = query<RealmGlobalAccountData>("type == $0 && key == $1", firstKey, secondKey).first()
}
