package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import de.connect2x.trixnity.client.store.repository.OutboundMegolmSessionRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession

internal class IndexedDBOutboundMegolmSessionRepository(
    json: Json
) : OutboundMegolmSessionRepository,
    IndexedDBFullRepository<RoomId, StoredOutboundMegolmSession>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.full) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "outbound_megolm_session"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            if (oldVersion < 1) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}