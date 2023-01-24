package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

@Serializable
data class IndexedDBInboundMegolmSession(
    val value: StoredInboundMegolmSession,
    val hasBeenBackedUp: Int,
)

fun IndexedDBInboundMegolmSession.toStoredInboundMegolmSession() = value

fun StoredInboundMegolmSession.toIndexedDBInboundMegolmSession() =
    IndexedDBInboundMegolmSession(
        value = this,
        hasBeenBackedUp = if (hasBeenBackedUp) 1 else 0,
    )

internal class IndexedDBInboundMegolmSessionRepository(
    private val json: Json,
) : InboundMegolmSessionRepository, IndexedDBRepository(objectStoreName) {

    // We need this, because hasBeenBackedUp cannot be indexed as boolean.
    private val internalRepository =
        IndexedDBMinimalStoreRepository<InboundMegolmSessionRepositoryKey, IndexedDBInboundMegolmSession>(
            objectStoreName = objectStoreName,
            keySerializer = { arrayOf(it.roomId.full, it.sessionId) },
            valueSerializer = serializer(),
            json = json
        )

    companion object {
        const val objectStoreName = "inbound_megolm_session"
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 -> {
                    database.createObjectStore(objectStoreName).apply {
                        createIndex("hasBeenBackedUp", KeyPath("hasBeenBackedUp"), unique = false)
                    }
                }
            }
        }
    }

    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withIndexedDBRead { store ->
        store.index("hasBeenBackedUp").openCursor(Key(0), autoContinue = true)
            .mapNotNull { json.decodeFromDynamicNullable(internalRepository.valueSerializer, it.value) }
            .map { it.toStoredInboundMegolmSession() }
            .toSet()
    }

    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? =
        internalRepository.get(key)?.toStoredInboundMegolmSession()

    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) =
        internalRepository.save(key, value.toIndexedDBInboundMegolmSession())

    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) =
        internalRepository.delete(key)

    override suspend fun deleteAll() =
        internalRepository.deleteAll()
}