package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import net.folivo.trixnity.client.store.repository.MapRepository

fun VersionChangeTransaction.createIndexedDBTwoDimensionsStoreRepository(
    database: Database,
    objectStoreName: String,
    keyPath: KeyPath,
    firstKeyIndexName: String,
    firstKeyIndexKeyPath: KeyPath,
    block: ObjectStore.() -> Unit = {},
) {
    database.createObjectStore(objectStoreName, keyPath).apply {
        createIndex(firstKeyIndexName, firstKeyIndexKeyPath, unique = false)
        block()
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

    protected fun keyOf(keys: Array<String>) =
        Key(keys.first(), *keys.drop(1).toTypedArray())

    override suspend fun get(firstKey: K1): Map<K2, V> = withIndexedDBRead { store ->
        store.index(firstKeyIndexName)
            .openCursor(keyOf(firstKeySerializer(firstKey)), autoContinue = true)
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
                item = json.encodeToDynamic(
                    representationSerializer,
                    mapToRepresentation(firstKey, secondKey, value)
                )
            )
            Unit
        }

    override suspend fun delete(firstKey: K1, secondKey: K2): Unit = withIndexedDBWrite { store ->
        store.delete(keyOf(firstKeySerializer(firstKey) + secondKeySerializer(secondKey)))
    }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}