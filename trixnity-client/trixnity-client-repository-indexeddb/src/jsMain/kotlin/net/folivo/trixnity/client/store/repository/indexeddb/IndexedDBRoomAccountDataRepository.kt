package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.events.Event

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBRoomAccountDataRepository(
    json: Json
) : RoomAccountDataRepository,
    IndexedDBTwoDimensionsStoreRepository<RoomAccountDataRepositoryKey, String, Event.RoomAccountDataEvent<*>>(
        objectStoreName = objectStoreName,
        firstKeySerializer = { arrayOf(it.roomId.full, it.type) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDeserializer = { it.first() },
        valueSerializer = json.serializersModule.getContextual(Event.RoomAccountDataEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer"),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_account_data"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBTwoDimensionsStoreRepository(database, oldVersion, objectStoreName)
    }
}