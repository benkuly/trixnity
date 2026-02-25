package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.MapRepository
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedObjectStore
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

fun WrappedTransaction.createIndexedDBTwoDimensionsStoreRepository(
    database: IDBDatabase,
    objectStoreName: String,
    keyPath: KeyPath?,
    firstKeyIndexName: String,
    firstKeyIndexKeyPath: KeyPath,
    block: WrappedTransaction.(WrappedObjectStore) -> Unit = {},
) {
    createObjectStore(database, objectStoreName, keyPath).apply {
        createIndex(firstKeyIndexName, firstKeyIndexKeyPath, unique = false)
        block(this)
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal abstract class IndexedDBMapRepository<K1, K2, V, R : Any>(
    objectStoreName: String,
    val firstKeyIndexName: String,
    val firstKeySerializer: (K1) -> Array<String>,
    val secondKeySerializer: (K2) -> Array<String>,
    val secondKeyDestructor: (R) -> K2,
    val mapToRepresentation: (K1, K2, V) -> R,
    val mapFromRepresentation: (R) -> V,
    val representationSerializer: KSerializer<R>,
    val json: Json
) : MapRepository<K1, K2, V>, IndexedDBRepository(objectStoreName) {

    override suspend fun get(firstKey: K1): Map<K2, V> = withIndexedDBRead { store ->
        store.index(firstKeyIndexName).openCursor(keyOf(firstKeySerializer(firstKey)))
            .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
            .map { secondKeyDestructor(it) to mapFromRepresentation(it) }
            .toList()
            .associate { it.first to it.second }
    }

    override suspend fun get(firstKey: K1, secondKey: K2): V? = withIndexedDBRead { store ->
        json.decodeFromDynamicNullable(
            representationSerializer,
            store.get(keyOf(firstKeySerializer(firstKey) + secondKeySerializer(secondKey)))
        )?.let(mapFromRepresentation)
    }

    override suspend fun save(firstKey: K1, secondKey: K2, value: V): Unit =
        withIndexedDBWrite { store ->
            store.put(
                value = json.encodeToDynamic(
                    representationSerializer,
                    mapToRepresentation(firstKey, secondKey, value)
                )
            )
        }

    override suspend fun delete(firstKey: K1, secondKey: K2): Unit = withIndexedDBWrite { store ->
        store.delete(keyOf(firstKeySerializer(firstKey) + secondKeySerializer(secondKey)))
    }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}