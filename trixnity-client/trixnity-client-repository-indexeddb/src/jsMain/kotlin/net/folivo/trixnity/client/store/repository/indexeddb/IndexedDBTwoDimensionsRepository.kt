package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.*
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import net.folivo.trixnity.client.store.repository.TwoDimensionsRepository

fun VersionChangeTransaction.migrateIndexedDBTwoDimensionsStoreRepository(
    database: Database,
    oldVersion: Int,
    objectStoreName: String
) {
    when {
        oldVersion < 1 -> {
            database.createObjectStore(objectStoreName, KeyPath("firstKey", "secondKey")).apply {
                createIndex("firstKey", KeyPath("firstKey"), unique = false)
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal abstract class IndexedDBTwoDimensionsRepository<K1, K2, V>(
    objectStoreName: String,
    val firstKeySerializer: (K1) -> Array<String>,
    val secondKeySerializer: (K2) -> Array<String>,
    val secondKeyDeserializer: (Array<String>) -> K2,
    valueSerializer: KSerializer<V>,
    val json: Json
) : TwoDimensionsRepository<K1, K2, V>, IndexedDBRepository(objectStoreName) {

    @Serializable
    internal data class IndexedDBTwoDimensionsStoreRepositoryEntry<V>(
        val firstKey: List<String>,
        val secondKey: List<String>,
        val value: V,
    )

    private val serializer = IndexedDBTwoDimensionsStoreRepositoryEntry.serializer(valueSerializer)

    override suspend fun get(key: K1): Map<K2, V> = withIndexedDBRead { store ->
        store.index("firstKey").openCursor(Key(firstKeySerializer(key)), autoContinue = true)
            .mapNotNull { json.decodeFromDynamicNullable(serializer, it.value) }
            .toList()
            .associate { secondKeyDeserializer(it.secondKey.toTypedArray()) to it.value }
    }

    override suspend fun getBySecondKey(firstKey: K1, secondKey: K2): V? = withIndexedDBRead { store ->
        json.decodeFromDynamicNullable(
            serializer,
            store.get(Key(arrayOf(firstKeySerializer(firstKey), secondKeySerializer(secondKey))))
        )?.value
    }

    override suspend fun save(key: K1, value: Map<K2, V>) = withIndexedDBWrite { store ->
        value.forEach { entry ->
            putToStore(store, key, entry.key, entry.value)
        }
    }

    override suspend fun saveBySecondKey(firstKey: K1, secondKey: K2, value: V): Unit =
        withIndexedDBWrite { store ->
            putToStore(store, firstKey, secondKey, value)
        }

    private suspend fun WriteTransaction.putToStore(
        store: ObjectStore,
        firstKey: K1,
        secondKey: K2,
        value: V
    ) {
        val firstKeyString = firstKeySerializer(firstKey)
        val secondKeyString = secondKeySerializer(secondKey)
        store.put(
            item = json.encodeToDynamic(
                serializer,
                IndexedDBTwoDimensionsStoreRepositoryEntry(
                    firstKey = firstKeyString.toList(),
                    secondKey = secondKeyString.toList(),
                    value = value,
                )
            ),
        )
    }

    override suspend fun delete(key: K1): Unit = withIndexedDBWrite { store ->
        store.index("firstKey").openKeyCursor(autoContinue = true)
            .collect { store.delete(Key(it.primaryKey)) }
    }

    override suspend fun deleteBySecondKey(firstKey: K1, secondKey: K2): Unit = withIndexedDBWrite { store ->
        store.delete(Key(arrayOf(firstKeySerializer(firstKey), secondKeySerializer(secondKey))))
    }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}