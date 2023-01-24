package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository

fun VersionChangeTransaction.migrateIndexedDBMinimalStoreRepository(
    database: Database,
    oldVersion: Int,
    objectStoreName: String
) {
    when {
        oldVersion < 1 -> {
            database.createObjectStore(objectStoreName)
        }
    }
}

internal open class IndexedDBMinimalStoreRepository<K, V : Any>(
    objectStoreName: String,
    val keySerializer: (K) -> Array<String>,
    val valueSerializer: KSerializer<V>,
    val json: Json,
) : MinimalStoreRepository<K, V>, IndexedDBRepository(objectStoreName) {

    override suspend fun get(key: K): V? = withIndexedDBRead { store ->
        json.decodeFromDynamicNullable(valueSerializer, store.get(Key(keySerializer(key))))
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun save(key: K, value: V): Unit = withIndexedDBWrite { store ->
        store.put(item = json.encodeToDynamic(valueSerializer, value), key = Key(keySerializer(key)))
        Unit
    }

    override suspend fun delete(key: K): Unit = withIndexedDBWrite { store ->
        store.delete(Key(keySerializer(key)))
    }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}