package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

internal class IndexedDBRoomUserRepository(
    json: Json
) : RoomUserRepository,
    IndexedDBTwoDimensionsStoreRepository<RoomId, UserId, RoomUser>(
        objectStoreName = objectStoreName,
        firstKeySerializer = { arrayOf(it.full) },
        secondKeySerializer = { arrayOf(it.full) },
        secondKeyDeserializer = { UserId(it.first()) },
        valueSerializer = serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_user"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBTwoDimensionsStoreRepository(database, oldVersion, objectStoreName)
    }
}