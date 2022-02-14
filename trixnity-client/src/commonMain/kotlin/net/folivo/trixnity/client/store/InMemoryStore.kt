package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

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
    keyChainLinkRepository = InMemoryKeyChainLinkRepository(),
    secretsRepository = InMemoryMinimalStoreRepository(),
    secretKeyRequestRepository = InMemorySecretKeyRequestRepository(),
    olmAccountRepository = InMemoryMinimalStoreRepository(),
    olmSessionRepository = InMemoryMinimalStoreRepository(),
    inboundMegolmSessionRepository = InMemoryInboundMegolmSessionRepository(),
    inboundMegolmMessageIndexRepository = InMemoryMinimalStoreRepository(),
    outboundMegolmSessionRepository = InMemoryMinimalStoreRepository(),
    roomRepository = InMemoryRoomRepository(),
    roomUserRepository = InMemoryTwoDimensionsStoreRepository(),
    roomStateRepository = InMemoryTwoDimensionsStoreRepository(),
    roomTimelineEventRepository = InMemoryMinimalStoreRepository(),
    roomOutboxMessageRepository = InMemoryRoomOutboxMessageRepository(),
    mediaRepository = InMemoryMediaRepository(),
    uploadMediaRepository = InMemoryMinimalStoreRepository(),
    globalAccountDataRepository = InMemoryTwoDimensionsStoreRepository(),
    roomAccountDataRepository = InMemoryTwoDimensionsStoreRepository()
)

class InMemoryStoreFactory(private val store: InMemoryStore) : StoreFactory {
    override suspend fun createStore(contentMappings: EventContentSerializerMappings, json: Json): Store {
        return store
    }
}

open class InMemoryMinimalStoreRepository<K, V> : MinimalStoreRepository<K, V> {
    val content = MutableStateFlow<Map<K, V>>(mapOf())
    override suspend fun get(key: K): V? = content.value[key]

    override suspend fun save(key: K, value: V) {
        content.update { it + (key to value) }
    }

    override suspend fun delete(key: K) {
        content.update { it - key }
    }

    override suspend fun deleteAll() {
        content.value = mapOf()
    }
}

class InMemoryTwoDimensionsStoreRepository<K1, K2, V> : TwoDimensionsStoreRepository<K1, K2, V>,
    InMemoryMinimalStoreRepository<K1, Map<K2, V>>() {
    override suspend fun getBySecondKey(firstKey: K1, secondKey: K2): V? =
        get(firstKey)?.get(secondKey)

    override suspend fun saveBySecondKey(firstKey: K1, secondKey: K2, value: V) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) + (secondKey to value))) }
    }

    override suspend fun deleteBySecondKey(firstKey: K1, secondKey: K2) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) - secondKey)) }
    }
}

class InMemorySecretKeyRequestRepository : SecretKeyRequestRepository,
    InMemoryMinimalStoreRepository<String, StoredSecretKeyRequest>() {
    override suspend fun getAll(): List<StoredSecretKeyRequest> = content.value.values.toList()
}

class InMemoryInboundMegolmSessionRepository : InboundMegolmSessionRepository,
    InMemoryMinimalStoreRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>() {
    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> =
        content.value.values.filter { it.hasBeenBackedUp.not() }.toSet()
}

class InMemoryRoomRepository : RoomRepository, InMemoryMinimalStoreRepository<RoomId, Room>() {
    override suspend fun getAll(): List<Room> = content.value.values.toList()
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

class InMemoryKeyChainLinkRepository : KeyChainLinkRepository {
    private val values = MutableStateFlow<Set<KeyChainLink>>(setOf())
    override suspend fun save(keyChainLink: KeyChainLink) {
        values.update { it + keyChainLink }
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> {
        return values.value.filter { it.signingUserId == signingUserId && it.signingKey == signingKey }.toSet()
    }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) {
        values.update {
            it.filter { value -> value.signedUserId == signedUserId && value.signedKey == signedKey }.toSet()
        }
    }

    override suspend fun deleteAll() {
        values.value = setOf()
    }
}