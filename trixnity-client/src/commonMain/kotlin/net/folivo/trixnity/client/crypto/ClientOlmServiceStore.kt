package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.key.get
import net.folivo.trixnity.client.key.getDeviceKey
import net.folivo.trixnity.client.key.waitForUpdateOutdatedKey
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.members
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.*

class ClientOlmServiceStore(private val store: Store, private val userService: IUserService) : OlmServiceStore {
    override suspend fun getCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? =
        store.keys.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun getEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? =
        store.keys.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? =
        store.keys.getDeviceKeys(userId)?.values?.map { it.value.signed }
            ?.find { it.keys.keys.any { key -> key.value == senderKey.value } }

    override suspend fun updateOlmSessions(
        senderKey: Key.Curve25519Key,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = store.olm.updateOlmSessions(senderKey, updater)

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = store.olm.updateOutboundMegolmSession(roomId, updater)

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = store.olm.updateInboundMegolmSession(sessionId, roomId, updater)


    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        store.olm.getInboundMegolmSession(sessionId, roomId)

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = store.olm.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex, updater)

    override val olmAccount = store.olm.account

    override suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>> {
        userService.loadMembers(roomId)
        store.room.get(roomId).first { it?.membersLoaded == true }
        val members = store.roomState.members(roomId, Membership.JOIN, Membership.INVITE)
        store.keys.waitForUpdateOutdatedKey(members)
        return members.mapNotNull { userId ->
            store.keys.getDeviceKeys(userId)?.let { userId to it.keys }
        }.toMap()
    }

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        store.room.get(roomId).value?.encryptionAlgorithm
}