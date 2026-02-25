package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.FullRepository
import de.connect2x.trixnity.idb.utils.WrappedObjectStore
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase
import web.idb.IDBValidKey
import kotlin.js.toJsArray

fun WrappedTransaction.createIndexedDBMinimalStoreRepository(
    database: IDBDatabase,
    objectStoreName: String,
    block: WrappedTransaction.(WrappedObjectStore) -> Unit = {},
) {
    createObjectStore(database, objectStoreName).apply {
        block(this)
    }
}

internal abstract class IndexedDBFullRepository<K, V : Any>(
    objectStoreName: String,
    val keySerializer: (K) -> Array<String>,
    val valueSerializer: KSerializer<V>,
    val json: Json,
) : FullRepository<K, V>, IndexedDBRepository(objectStoreName) {

    override suspend fun get(key: K): V? = withIndexedDBRead { store ->
        json.decodeFromDynamicNullable(
            valueSerializer,
            store.get(backwardsCompatibleKeyOf(keySerializer(key))),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun save(key: K, value: V): Unit = withIndexedDBWrite { store ->
        store.put(
            value = json.encodeToDynamic(valueSerializer, value),
            key = backwardsCompatibleKeyOf(keySerializer(key)),
        )
    }

    override suspend fun delete(key: K): Unit = withIndexedDBWrite { store ->
        store.delete(backwardsCompatibleKeyOf(keySerializer(key)))
    }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }

    override suspend fun getAll(): List<V> = withIndexedDBRead { store ->
        store.openCursor()
            .mapNotNull { json.decodeFromDynamicNullable(valueSerializer, it.value) }
            .toList()
    }
}

// This keyOf implementation always encodes a composite key as array while the default
// implementation will encode arrays with one element as the element itself.
// This is necessary to stay backwards compatible to the previous implementation.
private fun backwardsCompatibleKeyOf(keys: Array<String>): IDBValidKey =
    IDBValidKey(keys.map(::IDBValidKey).toJsArray())