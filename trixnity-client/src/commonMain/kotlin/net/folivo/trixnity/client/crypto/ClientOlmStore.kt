package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.key.get
import net.folivo.trixnity.client.key.getDeviceKey
import net.folivo.trixnity.client.key.waitForUpdateOutdatedKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
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
    accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val userService: UserService
) : net.folivo.trixnity.crypto.olm.OlmStore {

    private suspend fun getLocalCurve25519Key(userId: UserId, deviceId: String) =
        keyStore.getDeviceKey(userId, deviceId).first()?.value?.get<Key.Curve25519Key>()

    override suspend fun findCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? =
        getLocalCurve25519Key(userId, deviceId) ?: run {
            keyStore.updateOutdatedKeys { it + userId }
            keyStore.waitForUpdateOutdatedKey(userId)
            getLocalCurve25519Key(userId, deviceId)
        }

    private suspend fun getLocalEd25519Key(userId: UserId, deviceId: String) =
        keyStore.getDeviceKey(userId, deviceId).first()?.value?.get<Key.Ed25519Key>()

    override suspend fun findEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? =
        getLocalEd25519Key(userId, deviceId) ?: run {
            keyStore.updateOutdatedKeys { it + userId }
            keyStore.waitForUpdateOutdatedKey(userId)
            getLocalEd25519Key(userId, deviceId)
        }

    private suspend fun getLocalDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key) =
        keyStore.getDeviceKeys(userId).first()?.values?.map { it.value.signed }
            ?.find { it.keys.keys.any { key -> key.value == senderKey.value } }

    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? =
        getLocalDeviceKeys(userId, senderKey) ?: run {
            keyStore.updateOutdatedKeys { it + userId }
            keyStore.waitForUpdateOutdatedKey(userId)
            getLocalDeviceKeys(userId, senderKey)
        }


    override suspend fun updateOlmSessions(
        senderKey: Key.Curve25519Key,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmCryptoStore.updateOlmSessions(senderKey, updater)

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = olmCryptoStore.updateOutboundMegolmSession(roomId, updater)

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = olmCryptoStore.updateInboundMegolmSession(sessionId, roomId, updater)


    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        olmCryptoStore.getInboundMegolmSession(sessionId, roomId).first()

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = olmCryptoStore.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex, updater)

    override val olmAccount = olmCryptoStore.account
    override val olmPickleKey = requireNotNull(accountStore.olmPickleKey.value)
    override val forgetFallbackKeyAfter = olmCryptoStore.forgetFallbackKeyAfter

    override suspend fun getDevices(roomId: RoomId, memberships: Set<Membership>): Map<UserId, Set<String>> {
        userService.loadMembers(roomId)
        val members = roomStateStore.members(roomId, memberships)
        keyStore.waitForUpdateOutdatedKey(members)
        return members.mapNotNull { userId ->
            keyStore.getDeviceKeys(userId).first()?.let { userId to it.keys }
        }.toMap()
    }

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? =
        roomStateStore.getByStateKey<HistoryVisibilityEventContent>(roomId).first()?.content?.historyVisibility

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        roomStore.get(roomId).first()?.encryptionAlgorithm
}