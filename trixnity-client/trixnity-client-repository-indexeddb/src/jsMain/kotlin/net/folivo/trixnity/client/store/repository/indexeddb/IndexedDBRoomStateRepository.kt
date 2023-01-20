package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.events.Event

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBRoomStateRepository(
    json: Json
) : RoomStateRepository,
    IndexedDBTwoDimensionsStoreRepository<RoomStateRepositoryKey, String, Event<*>>(
        objectStoreName = objectStoreName,
        firstKeySerializer = { arrayOf(it.roomId.full, it.type) },
        secondKeySerializer = { arrayOf(it) },
        secondKeyDeserializer = { it.first() },
        valueSerializer = json.serializersModule.getContextual(Event::class)
            ?: throw IllegalArgumentException("could not find event serializer"),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_state"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBTwoDimensionsStoreRepository(database, oldVersion, objectStoreName)
    }
}