package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.ObjectStore
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import de.connect2x.trixnity.client.store.repository.FullRepository

fun VersionChangeTransaction.createIndexedDBMinimalStoreRepository(
    database: Database,
    objectStoreName: String,
    block: ObjectStore.() -> Unit = {},
) {
    database.createObjectStore(objectStoreName).apply {
        block()
    }
}

internal abstract class IndexedDBFullRepository<K, V : Any>(
    objectStoreName: String,
    val keySerializer: (K) -> Array<String>,
    val valueSerializer: KSerializer<V>,
    val json: Json,
) : FullRepository<K, V>, IndexedDBRepository(objectStoreName) {

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

    override suspend fun getAll(): List<V> = withIndexedDBRead { store ->
        store.openCursor(autoContinue = true)
            .mapNotNull { json.decodeFromDynamicNullable(valueSerializer, it.value) }
            .toList()
    }
}