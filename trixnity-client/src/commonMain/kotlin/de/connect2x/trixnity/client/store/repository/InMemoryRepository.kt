package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomOutboxMessage
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.StoredRoomKeyRequest
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.client.store.StoredSecretKeyRequest
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

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
        content.value = emptyMap()
    }
}

abstract class InMemoryFullRepository<K, V> : FullRepository<K, V>, InMemoryMinimalRepository<K, V>() {
    override suspend fun getAll(): List<V> = content.value.values.toList()
}

abstract class InMemoryMapRepository<K1, K2, V> : MapRepository<K1, K2, V> {
    val content = MutableStateFlow<Map<K1, Map<K2, V>>>(mapOf())
    override suspend fun get(firstKey: K1): Map<K2, V> =
        content.value[firstKey].orEmpty()

    override suspend fun get(firstKey: K1, secondKey: K2): V? =
        content.value[firstKey]?.get(secondKey)

    override suspend fun save(firstKey: K1, secondKey: K2, value: V) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) + (secondKey to value))) }
    }

    override suspend fun delete(firstKey: K1, secondKey: K2) {
        content.update { it + (firstKey to ((it[firstKey] ?: mapOf()) - secondKey)) }
    }

    override suspend fun deleteAll() {
        content.value = emptyMap()
    }
}

class InMemoryAccountRepository : AccountRepository, InMemoryMinimalRepository<Long, Account>()
class InMemoryAuthenticationRepository : AuthenticationRepository, InMemoryMinimalRepository<Long, Authentication>()

class InMemoryServerDataRepository : ServerDataRepository, InMemoryMinimalRepository<Long, ServerData>()
class InMemoryOutdatedKeysRepository : OutdatedKeysRepository, InMemoryMinimalRepository<Long, Set<UserId>>()
class InMemoryDeviceKeysRepository : DeviceKeysRepository,
    InMemoryMinimalRepository<UserId, Map<String, StoredDeviceKeys>>()

class InMemoryCrossSigningKeysRepository : CrossSigningKeysRepository,
    InMemoryMinimalRepository<UserId, Set<StoredCrossSigningKeys>>()

class InMemoryKeyVerificationStateRepository : KeyVerificationStateRepository,
    InMemoryMinimalRepository<KeyVerificationStateKey, KeyVerificationState>()

class InMemorySecretsRepository : SecretsRepository,
    InMemoryMinimalRepository<Long, Map<SecretType, StoredSecret>>()

class InMemoryOlmAccountRepository : OlmAccountRepository, InMemoryMinimalRepository<Long, String>()
class InMemoryOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository,
    InMemoryMinimalRepository<Long, Instant>()

class InMemoryOlmSessionRepository : OlmSessionRepository,
    InMemoryFullRepository<Curve25519KeyValue, Set<StoredOlmSession>>()

class InMemoryInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository,
    InMemoryMinimalRepository<InboundMegolmMessageIndexRepositoryKey, StoredInboundMegolmMessageIndex>()

class InMemoryOutboundMegolmSessionRepository : OutboundMegolmSessionRepository,
    InMemoryFullRepository<RoomId, StoredOutboundMegolmSession>()

class InMemoryRoomUserRepository : RoomUserRepository, InMemoryMapRepository<RoomId, UserId, RoomUser>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { it - roomId }
    }
}

class InMemoryRoomUserReceiptsRepository : RoomUserReceiptsRepository,
    InMemoryMapRepository<RoomId, UserId, RoomUserReceipts>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { it - roomId }
    }
}

class InMemoryRoomStateRepository : RoomStateRepository,
    InMemoryMapRepository<RoomStateRepositoryKey, String, ClientEvent.StateBaseEvent<*>>() {
    override suspend fun getByRooms(
        roomIds: Set<RoomId>,
        type: String,
        stateKey: String
    ): List<ClientEvent.StateBaseEvent<*>> =
        content.value.filterKeys { roomIds.contains(it.roomId) && it.type == type }
            .values.flatMap { entry -> entry.filterKeys { it == stateKey }.values }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }
}

class InMemoryTimelineEventRepository : TimelineEventRepository,
    InMemoryMinimalRepository<TimelineEventKey, TimelineEvent>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }
}

class InMemoryTimelineEventRelationRepository : TimelineEventRelationRepository,
    InMemoryMapRepository<TimelineEventRelationKey, EventId, TimelineEventRelation>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }

}

@MSC4354
class InMemoryStickyEventRepository : StickyEventRepository,
    InMemoryMapRepository<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>>() {
    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>> =
        content.value
            .flatMap { (key, values) ->
                values.filterValues {
                    it.endTime < before
                }.map { key to it.key }
            }.toSet()

    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }

    override suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>? =
        content.value.entries.firstNotNullOfOrNull { (key, values) ->
            values.entries.find { it.value.event.roomId == roomId && it.value.event.id == eventId }?.let { key to it }
        }?.let { it.first to it.second.key }
}

class InMemoryMediaCacheMappingRepository : MediaCacheMappingRepository,
    InMemoryMinimalRepository<String, MediaCacheMapping>()

class InMemoryGlobalAccountDataRepository : GlobalAccountDataRepository,
    InMemoryMapRepository<String, String, GlobalAccountDataEvent<*>>()

class InMemoryUserPresenceRepository : UserPresenceRepository,
    InMemoryMinimalRepository<UserId, UserPresence>()

class InMemoryRoomAccountDataRepository : RoomAccountDataRepository,
    InMemoryMapRepository<RoomAccountDataRepositoryKey, String, RoomAccountDataEvent<*>>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }
}

class InMemorySecretKeyRequestRepository : SecretKeyRequestRepository,
    InMemoryFullRepository<String, StoredSecretKeyRequest>()

class InMemoryRoomKeyRequestRepository : RoomKeyRequestRepository,
    InMemoryFullRepository<String, StoredRoomKeyRequest>()

class InMemoryInboundMegolmSessionRepository : InboundMegolmSessionRepository,
    InMemoryFullRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession>() {
    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> =
        content.value.values.filter { it.hasBeenBackedUp.not() }.toSet()
}

class InMemoryRoomRepository : RoomRepository, InMemoryFullRepository<RoomId, Room>()

class InMemoryRoomOutboxMessageRepository : RoomOutboxMessageRepository,
    InMemoryFullRepository<RoomOutboxMessageRepositoryKey, RoomOutboxMessage<*>>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterKeys { it.roomId != roomId } }
    }
}

class InMemoryNotificationRepository : NotificationRepository, InMemoryFullRepository<String, StoredNotification>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterValues { it.roomId != roomId } }
    }
}

class InMemoryNotificationUpdateRepository : NotificationUpdateRepository,
    InMemoryFullRepository<String, StoredNotificationUpdate>() {
    override suspend fun deleteByRoomId(roomId: RoomId) {
        content.update { value -> value.filterValues { it.roomId != roomId } }
    }
}

class InMemoryNotificationStateRepository : NotificationStateRepository,
    InMemoryFullRepository<RoomId, StoredNotificationState>()

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

class InMemoryMigrationRepository : MigrationRepository,
    InMemoryMinimalRepository<String, String>()
