package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event

@Serializable
internal class IndexedDBGlobalAccountData(
    val type: String,
    val key: String,
    @Contextual
    val value: Event.GlobalAccountDataEvent<*>,
)

internal class IndexedDBGlobalAccountDataRepository(
    json: Json
) : GlobalAccountDataRepository,
    IndexedDBMapRepository<String, String, Event.GlobalAccountDataEvent<*>, IndexedDBGlobalAccountData>(
        objectStoreName = IndexedDBRoomAccountDataRepository.objectStoreName,
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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 ->
                    createIndexedDBTwoDimensionsStoreRepository(
                        database = database,
                        objectStoreName = objectStoreName,
                        keyPath = KeyPath("type", "key"),
                        firstKeyIndexName = "type",
                        firstKeyIndexKeyPath = KeyPath("type"),
                    )
            }
        }
    }
}