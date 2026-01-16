package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.client.store.repository.NotificationStateRepository
import de.connect2x.trixnity.core.model.RoomId

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBNotificationStateRepository(
    json: Json
) : NotificationStateRepository,
    IndexedDBFullRepository<RoomId, StoredNotificationState>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.full) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "notification_state"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 8) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}