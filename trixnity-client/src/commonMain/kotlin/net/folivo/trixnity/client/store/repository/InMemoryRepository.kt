package net.folivo.trixnity.client.store.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelationType
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

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

open class InMemoryTwoDimensionsStoreRepository<K1, K2, V> : TwoDimensionsStoreRepository<K1, K2, V>,
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

class InMemoryAccountRepository : AccountRepository, InMemoryMinimalStoreRepository<Long, Account>()
class InMemoryOutdatedKeysRepository : OutdatedKeysRepository, InMemoryMinimalStoreRepository<Long, Set<UserId>>()
class InMemoryDeviceKeysRepository : DeviceKeysRepository,
    InMemoryMinimalStoreRepository<UserId, Map<String, StoredDeviceKeys>>()

class InMemoryCrossSigningKeysRepository : CrossSigningKeysRepository,
    InMemoryMinimalStoreRepository<UserId, Set<StoredCrossSigningKeys>>()

class InMemoryKeyVerificationStateRepository : KeyVerificationStateRepository,
    InMemoryMinimalStoreRepository<KeyVerificationStateKey, KeyVerificationState>()

class InMemorySecretsRepository : SecretsRepository,
    InMemoryMinimalStoreRepository<Long, Map<SecretType, StoredSecret>>()

class InMemoryOlmAccountRepository : OlmAccountRepository, InMemoryMinimalStoreRepository<Long, String>()
class InMemoryOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository,
    InMemoryMinimalStoreRepository<Long, Instant>()

class InMemoryOlmSessionRepository : OlmSessionRepository,
    InMemoryMinimalStoreRepository<Key.Curve25519Key, Set<StoredOlmSession>>()

class InMemoryInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository,
    InMemoryMinimalStoreRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>()

class InMemoryOutboundMegolmSessionRepository : OutboundMegolmSessionRepository,
    InMemoryMinimalStoreRepository<RoomId, StoredOutboundMegolmSession>()

class InMemoryRoomUserRepository : RoomUserRepository, InMemoryTwoDimensionsStoreRepository<RoomId, UserId, RoomUser>()
class InMemoryRoomStateRepository : RoomStateRepository,
    InMemoryTwoDimensionsStoreRepository<RoomStateRepositoryKey, String, Event<*>>()

class InMemoryTimelineEventRepository : TimelineEventRepository,
    InMemoryMinimalStoreRepository<TimelineEventKey, TimelineEvent>()

class InMemoryTimelineEventRelationRepository : TimelineEventRelationRepository,
    InMemoryTwoDimensionsStoreRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>>()

class InMemoryMediaCacheMappingRepository : MediaCacheMappingRepository,
    InMemoryMinimalStoreRepository<String, MediaCacheMapping>()

class InMemoryGlobalAccountDataRepository : GlobalAccountDataRepository,
    InMemoryTwoDimensionsStoreRepository<String, String, Event.GlobalAccountDataEvent<*>>()

class InMemoryRoomAccountDataRepository : RoomAccountDataRepository,
    InMemoryTwoDimensionsStoreRepository<RoomAccountDataRepositoryKey, String, Event.RoomAccountDataEvent<*>>()

class InMemorySecretKeyRequestRepository : SecretKeyRequestRepository,
    InMemoryMinimalStoreRepository<String, StoredSecretKeyRequest>() {
    override suspend fun getAll(): List<StoredSecretKeyRequest> = content.value.values.toList()
}

class InMemoryRoomKeyRequestRepository : RoomKeyRequestRepository,
    InMemoryMinimalStoreRepository<String, StoredRoomKeyRequest>() {
    override suspend fun getAll(): List<StoredRoomKeyRequest> = content.value.values.toList()
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
    InMemoryMinimalStoreRepository<String, RoomOutboxMessage<*>>() {
    override suspend fun getAll(): List<RoomOutboxMessage<*>> = content.value.values.toList()
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