package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import web.idb.IDBDatabase
import web.idb.IDBKeyRange
import kotlin.js.toJsNumber
import kotlin.time.Instant

@Serializable
@MSC4354
internal class IndexedDBStickyEvent(
    val roomId: String,
    val type: String,
    val sender: UserId,
    val stickyKey: String,
    val endTimeMs: Long,
    val value: StoredStickyEvent<StickyEventContent>,
)

@OptIn(MSC4354::class)
internal class IndexedDBStickyEventRepository(
    json: Json,
) : StickyEventRepository,
    IndexedDBMapRepository<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>, IndexedDBStickyEvent>(
        objectStoreName = objectStoreName,
        firstKeyIndexName = "roomId|type",
        firstKeySerializer = { arrayOf(it.roomId.full, it.type) },
        secondKeySerializer = { arrayOf(it.sender.full, dbStickyKey(it.stickyKey)) },
        secondKeyDestructor = { StickyEventRepositorySecondKey(it.sender, originalStickyKey(it.stickyKey)) },
        mapToRepresentation = { k1, k2, v ->
            IndexedDBStickyEvent(
                roomId = k1.roomId.full,
                type = k1.type,
                sender = k2.sender,
                stickyKey = dbStickyKey(k2.stickyKey),
                endTimeMs = v.endTime.toEpochMilliseconds(),
                value = v
            )
        },
        mapFromRepresentation = { it.value },
        representationSerializer = IndexedDBStickyEvent.serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "sticky_event"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 10)
                createIndexedDBTwoDimensionsStoreRepository(
                    database = database,
                    objectStoreName = objectStoreName,
                    keyPath = KeyPath.Multiple("roomId", "type", "sender", "stickyKey"),
                    firstKeyIndexName = "roomId|type",
                    firstKeyIndexKeyPath = KeyPath.Multiple("roomId", "type"),
                ) { store ->
                    store.createIndex("roomId", KeyPath.Single("roomId"), unique = false)
                    store.createIndex(
                        "endTimeMs",
                        KeyPath.Single("endTimeMs"),
                        unique = false
                    )
                    store.createIndex(
                        "roomId|eventId",
                        KeyPath.Multiple("roomId", "value.event.event_id"),
                        unique = false
                    )
                }
        }

        private const val STICKY_KEY_NULL = "NULL"
        private const val STICKY_KEY_NON_NULL_PREFIX = "V-"
        private fun dbStickyKey(stickyKey: String?): String =
            stickyKey?.let { STICKY_KEY_NON_NULL_PREFIX + it } ?: STICKY_KEY_NULL

        private fun originalStickyKey(stickyKey: String): String? =
            if (stickyKey == STICKY_KEY_NULL) null
            else stickyKey.removePrefix(STICKY_KEY_NON_NULL_PREFIX)
    }

    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>> =
        withIndexedDBRead { store ->
            store.index("endTimeMs")
                .openCursor(IDBKeyRange.upperBound(before.toEpochMilliseconds().toDouble().toJsNumber(), true))
                .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
                .map {
                    StickyEventRepositoryFirstKey(RoomId(it.roomId), it.type) to
                            StickyEventRepositorySecondKey(it.sender, originalStickyKey(it.stickyKey))
                }
                .toSet()
        }

    override suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>? =
        withIndexedDBRead { store ->
            store.index("roomId|eventId")
                .openCursor(keyOf(arrayOf(roomId.full, eventId.full)))
                .mapNotNull { json.decodeFromDynamicNullable(representationSerializer, it.value) }
                .map {
                    StickyEventRepositoryFirstKey(RoomId(it.roomId), it.type) to
                            StickyEventRepositorySecondKey(it.sender, originalStickyKey(it.stickyKey))
                }
                .firstOrNull()
        }

    override suspend fun deleteByRoomId(roomId: RoomId) = withIndexedDBWrite { store ->
        store.index("roomId").openCursor(keyOf(roomId.full))
            .collect {
                store.delete(it.primaryKey)
            }
    }
}
