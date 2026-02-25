package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.GlobalAccountDataRepository
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

@Serializable
internal class IndexedDBGlobalAccountData(
    @Suppress("unused") val type: String,
    val key: String,
    @Contextual
    val value: GlobalAccountDataEvent<*>,
)

internal class IndexedDBGlobalAccountDataRepository(
    json: Json
) : GlobalAccountDataRepository,
    IndexedDBMapRepository<String, String, GlobalAccountDataEvent<*>, IndexedDBGlobalAccountData>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "type",
        firstKeySerializer = { arrayOf(it) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDestructor = { it.key },
        mapToRepresentation = { k1, k2, v -> IndexedDBGlobalAccountData(k1, k2, v) },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBGlobalAccountData.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "global_account_data"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath.Multiple("type", "key"),
                    firstKeyIndexName = "type",
                    firstKeyIndexKeyPath = KeyPath.Single("type"),
                )
        }
    }
}