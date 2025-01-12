package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.key.get
import net.folivo.trixnity.client.key.getDeviceKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.LoadMembersService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession

class ClientOlmStore(
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val roomStateStore: RoomStateStore,
    private val loadMembersService: LoadMembersService,
) : net.folivo.trixnity.crypto.olm.OlmStore {

    override suspend fun findCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? =
        keyStore.getDeviceKey(userId, deviceId).first()?.value?.get<Key.Curve25519Key>()


    override suspend fun findEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? =
        keyStore.getDeviceKey(userId, deviceId).first()?.value?.get<Key.Ed25519Key>()

    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? =
        keyStore.getDeviceKeys(userId).first()?.values?.map { it.value.signed }
            ?.find { it.keys.keys.any { key -> key.value == senderKey.value } }


    override suspend fun updateOlmSessions(
        senderKey: Key.Curve25519Key,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) {
        olmCryptoStore.updateOlmSessions(senderKey, updater)
    }

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) {
        olmCryptoStore.updateOutboundMegolmSession(roomId, updater)
    }

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) {
        olmCryptoStore.updateInboundMegolmSession(sessionId, roomId, updater)
    }


    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) {
        olmCryptoStore.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex, updater)
    }

    override suspend fun getOlmAccount(): String = checkNotNull(olmCryptoStore.getOlmAccount())
    override suspend fun updateOlmAccount(updater: suspend (String) -> String) = olmCryptoStore.updateOlmAccount {
        updater(checkNotNull(it))
    }

    override suspend fun getOlmPickleKey(): String = checkNotNull(accountStore.getAccount()?.olmPickleKey)

    override suspend fun getForgetFallbackKeyAfter(): Flow<Instant?> = olmCryptoStore.getForgetFallbackKeyAfter()

    override suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?) =
        olmCryptoStore.updateForgetFallbackKeyAfter(updater)


    override suspend fun getDevices(roomId: RoomId, memberships: Set<Membership>): Map<UserId, Set<String>> {
        loadMembersService(roomId, true)
        val members = roomStateStore.members(roomId, memberships)
        return members.mapNotNull { userId ->
            keyStore.getDeviceKeys(userId).first()?.let { userId to it.keys }
        }.toMap()
    }

    override suspend fun getDevices(roomId: RoomId, userId: UserId): Set<String>? =
        keyStore.getDeviceKeys(userId).first()?.keys

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? =
        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(roomId).first()?.content?.historyVisibility

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).first()?.content?.algorithm
}

