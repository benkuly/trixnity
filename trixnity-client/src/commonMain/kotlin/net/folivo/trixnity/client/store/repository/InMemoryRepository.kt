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

abstract class InMemoryMinimalRepository<K, V> : MinimalRepository<K, V> {
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

abstract class InMemoryFullRepository<K, V> : FullRepository<K, V>, InMemoryMinimalRepository<K, V>() {
    override suspend fun getAll(): List<V> = content.value.values.toList()
}

abstract class InMemoryTwoDimensionsRepository<K1, K2, V> : TwoDimensionsRepository<K1, K2, V>,
    InMemoryMinimalRepository<K1, Map<K2, V>>() {
    override suspend fun getBySecondKey(firstKey: K1, secondKey: K2): V? =
        get(firstKey)?.get(secondKey)

    override suspend fun saveBySecondKey(firstKey: K1, secondKey: K2, value: V) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) + (secondKey to value))) }
    }

    override suspend fun deleteBySecondKey(firstKey: K1, secondKey: K2) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) - secondKey)) }
    }
}

class InMemoryAccountRepository : AccountRepository, InMemoryMinimalRepository<Long, Account>()
class InMemoryOutdatedKeysRepository : OutdatedKeysRepository, InMemoryMinimalRepository<Long, Set<UserId>>()
class InMemoryDeviceKeysRepository : DeviceKeysRepository,
    InMemoryMinimalRepository<UserId, Map<String, StoredDeviceKeys>>()

class InMemoryCrossSigningKeysRepository : CrossSigningKeysRepository,
    InMemoryMinimalRepository<UserId, Set<StoredCrossSigningKeys>>()

class InMemoryKeyVerificationStateRepository : KeyVerificationStateRepository,
    InMemoryMinimalRepository<VerifiedKeysRepositoryKey, KeyVerificationState>()

class InMemorySecretsRepository : SecretsRepository,
    InMemoryMinimalRepository<Long, Map<SecretType, StoredSecret>>()

class InMemoryOlmAccountRepository : OlmAccountRepository, InMemoryMinimalRepository<Long, String>()
class InMemoryOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository,
    InMemoryMinimalRepository<Long, Instant>()

class InMemoryOlmSessionRepository : OlmSessionRepository,
    InMemoryMinimalRepository<Key.Curve25519Key, Set<StoredOlmSession>>()

class InMemoryInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository,
    InMemoryMinimalRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>()

class InMemoryOutboundMegolmSessionRepository : OutboundMegolmSessionRepository,
    InMemoryMinimalRepository<RoomId, StoredOutboundMegolmSession>()

class InMemoryRoomUserRepository : RoomUserRepository, InMemoryTwoDimensionsRepository<RoomId, UserId, RoomUser>()
class InMemoryRoomStateRepository : RoomStateRepository,
    InMemoryTwoDimensionsRepository<RoomStateRepositoryKey, String, Event<*>>()

class InMemoryTimelineEventRepository : TimelineEventRepository,
    InMemoryMinimalRepository<TimelineEventKey, TimelineEvent>()

class InMemoryTimelineEventRelationRepository : TimelineEventRelationRepository,
    InMemoryTwoDimensionsRepository<TimelineEventRelationKey, RelationType, Set<TimelineEventRelation>>()

class InMemoryMediaCacheMappingRepository : MediaCacheMappingRepository,
    InMemoryMinimalRepository<String, MediaCacheMapping>()

class InMemoryGlobalAccountDataRepository : GlobalAccountDataRepository,
    InMemoryTwoDimensionsRepository<String, String, Event.GlobalAccountDataEvent<*>>()

class InMemoryRoomAccountDataRepository : RoomAccountDataRepository,
    InMemoryTwoDimensionsRepository<RoomAccountDataRepositoryKey, String, Event.RoomAccountDataEvent<*>>()

class InMemorySecretKeyRequestRepository : SecretKeyRequestRepository,
    InMemoryFullRepository<String, StoredSecretKeyRequest>()

class InMemoryRoomKeyRequestRepository : RoomKeyRequestRepository,
    InMemoryFullRepository<String, StoredRoomKeyRequest>()

class InMemoryInboundMegolmSessionRepository : InboundMegolmSessionRepository,
    InMemoryMinimalRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>() {
    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> =
        content.value.values.filter { it.hasBeenBackedUp.not() }.toSet()
}

class InMemoryRoomRepository : RoomRepository, InMemoryFullRepository<RoomId, Room>()

class InMemoryRoomOutboxMessageRepository : RoomOutboxMessageRepository,
    InMemoryFullRepository<String, RoomOutboxMessage<*>>()

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