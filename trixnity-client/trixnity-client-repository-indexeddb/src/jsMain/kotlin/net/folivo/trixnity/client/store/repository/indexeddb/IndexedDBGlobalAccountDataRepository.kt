package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBGlobalAccountDataRepository(
    json: Json
) : GlobalAccountDataRepository,
    IndexedDBTwoDimensionsRepository<String, String, Event.GlobalAccountDataEvent<*>>(
        objectStoreName = objectStoreName,
        firstKeySerializer = { arrayOf(it) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDeserializer = { it.first() },
        valueSerializer = json.serializersModule.getContextual(Event.GlobalAccountDataEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer"),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "global_account_data"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBTwoDimensionsStoreRepository(database, oldVersion, objectStoreName)
    }
}