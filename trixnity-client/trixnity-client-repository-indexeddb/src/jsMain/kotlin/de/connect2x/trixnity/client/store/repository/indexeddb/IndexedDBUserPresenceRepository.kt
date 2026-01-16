package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
import de.connect2x.trixnity.core.model.UserId

internal class IndexedDBUserPresenceRepository(
    json: Json
) : UserPresenceRepository,
    IndexedDBFullRepository<UserId, UserPresence>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.full) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "user_presence"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 7) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}