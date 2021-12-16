package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings

class InMemoryStore(storeCoroutineScope: CoroutineScope) : Store(
    scope = storeCoroutineScope,
    contentMappings = DefaultEventContentSerializerMappings,
    rtm = object : RepositoryTransactionManager {
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    },
    accountRepository = InMemoryMinimalStoreRepository(),
    outdatedKeysRepository = InMemoryMinimalStoreRepository(),
    deviceKeysRepository = InMemoryMinimalStoreRepository(),
    crossSigningKeysRepository = InMemoryMinimalStoreRepository(),
    keyVerificationStateRepository = InMemoryMinimalStoreRepository(),
    olmAccountRepository = InMemoryMinimalStoreRepository(),
    olmSessionRepository = InMemoryMinimalStoreRepository(),
    inboundMegolmSessionRepository = InMemoryMinimalStoreRepository(),
    inboundMegolmMessageIndexRepository = InMemoryMinimalStoreRepository(),
    outboundMegolmSessionRepository = InMemoryMinimalStoreRepository(),
    roomRepository = InMemoryRoomRepository(),
    roomUserRepository = InMemoryRoomUserRepository(),
    roomStateRepository = InMemoryRoomStateRepository(),
    roomTimelineRepository = InMemoryMinimalStoreRepository(),
    roomOutboxMessageRepository = InMemoryRoomOutboxMessageRepository(),
    mediaRepository = InMemoryMediaRepository(),
    uploadMediaRepository = InMemoryMinimalStoreRepository(),
    globalAccountDataRepository = InMemoryMinimalStoreRepository(),
    roomAccountDataRepository = InMemoryMinimalStoreRepository()
)

open class InMemoryMinimalStoreRepository<K, V> : MinimalStoreRepository<K, V> {
    val content = MutableStateFlow<Map<K, V>>(mapOf())
    override suspend fun get(key: K): V? = content.value[key]

    override suspend fun save(key: K, value: V) {
        content.update { it + (key to value) }
    }

    override suspend fun delete(key: K) {
        content.update { it - key }
    }
}

class InMemoryRoomRepository : RoomRepository, InMemoryMinimalStoreRepository<RoomId, Room>() {
    override suspend fun getAll(): List<Room> = content.value.values.toList()
}

class InMemoryRoomUserRepository : RoomUserRepository,
    InMemoryMinimalStoreRepository<RoomId, Map<UserId, RoomUser>>() {
    override suspend fun getByUserId(userId: UserId, roomId: RoomId): RoomUser? =
        get(roomId)?.get(userId)

    override suspend fun saveByUserId(userId: UserId, roomId: RoomId, roomUser: RoomUser) {
        content.update { it + (roomId to ((it[roomId] ?: mapOf()) + (userId to roomUser))) }
    }

    override suspend fun deleteByUserId(userId: UserId, roomId: RoomId) {
        content.update { it + (roomId to ((it[roomId] ?: mapOf()) - userId)) }
    }
}

class InMemoryRoomStateRepository : RoomStateRepository,
    InMemoryMinimalStoreRepository<RoomStateRepositoryKey, Map<String, Event<*>>>() {
    override suspend fun getByStateKey(key: RoomStateRepositoryKey, stateKey: String): Event<*>? =
        get(key)?.get(stateKey)

    override suspend fun saveByStateKey(key: RoomStateRepositoryKey, stateKey: String, event: Event<*>) {
        content.update { it + (key to ((it[key] ?: mapOf()) + (stateKey to event))) }
    }
}

class InMemoryRoomOutboxMessageRepository : RoomOutboxMessageRepository,
    InMemoryMinimalStoreRepository<String, RoomOutboxMessage>() {
    override suspend fun getAll(): List<RoomOutboxMessage> = content.value.values.toList()
}

class InMemoryMediaRepository : MediaRepository, InMemoryMinimalStoreRepository<String, ByteArray>() {
    override suspend fun changeUri(oldUri: String, newUri: String) {
        content.update {
            val value = it[oldUri]
            if (value != null) (it - oldUri) + (newUri to value)
            it
        }
    }
}