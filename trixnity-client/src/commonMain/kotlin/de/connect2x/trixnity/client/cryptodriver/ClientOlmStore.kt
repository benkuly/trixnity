package de.connect2x.trixnity.client.cryptodriver

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.key.getDeviceKey
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.client.user.LoadMembersService
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import kotlin.time.Instant

class ClientOlmStore(
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val roomStateStore: RoomStateStore,
    private val loadMembersService: LoadMembersService,
) : de.connect2x.trixnity.crypto.olm.OlmStore {

    override suspend fun getDeviceKeys(userId: UserId): Map<String, DeviceKeys>? =
        keyStore.getDeviceKeys(userId).first()?.mapValues { it.value.value.signed }

    override suspend fun getMembers(
        roomId: RoomId,
        memberships: Set<Membership>
    ): Set<UserId> {
        loadMembersService(roomId, true)
        return roomStateStore.members(roomId, memberships)
    }

    override suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel? =
        keyStore.getDeviceKey(userId, deviceId).first()?.trustLevel.toDeviceTrustLevel()

    override suspend fun updateOlmSessions(
        senderKeyValue: Curve25519KeyValue,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) {
        olmCryptoStore.updateOlmSessions(senderKeyValue, updater)
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

    override suspend fun getOlmPickleKey(): String? = checkNotNull(accountStore.getAccount()).olmPickleKey

    override suspend fun getForgetFallbackKeyAfter(): Flow<Instant?> = olmCryptoStore.getForgetFallbackKeyAfter()

    override suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?) =
        olmCryptoStore.updateForgetFallbackKeyAfter(updater)

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? =
        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(roomId).first()?.content?.historyVisibility

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        roomStateStore.getByStateKey<EncryptionEventContent>(roomId).first()?.content?.algorithm
}

