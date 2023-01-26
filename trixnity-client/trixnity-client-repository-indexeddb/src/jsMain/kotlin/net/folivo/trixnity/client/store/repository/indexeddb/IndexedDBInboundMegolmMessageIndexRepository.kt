package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex

internal class IndexedDBInboundMegolmMessageIndexRepository(
    json: Json,
) : InboundMegolmMessageIndexRepository,
    IndexedDBFullRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.roomId.full, it.sessionId, it.messageIndex.toString()) },
        valueSerializer = serializer(),
        json = json
    ) {
    companion object {
        const val objectStoreName = "inbound_megolm_message_index"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) =
            migrateIndexedDBMinimalStoreRepository(database, oldVersion, objectStoreName)
    }
}