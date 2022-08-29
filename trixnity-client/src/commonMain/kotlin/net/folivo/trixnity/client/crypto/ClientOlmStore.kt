package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.client.key.get
import net.folivo.trixnity.client.key.getDeviceKey
import net.folivo.trixnity.client.key.waitForUpdateOutdatedKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
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
    private val olmStore: OlmStore,
    private val keyStore: KeyStore,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val userService: IUserService
) : net.folivo.trixnity.crypto.olm.OlmStore {
    override suspend fun getCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? =
        keyStore.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun getEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? =
        keyStore.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? =
        keyStore.getDeviceKeys(userId)?.values?.map { it.value.signed }
            ?.find { it.keys.keys.any { key -> key.value == senderKey.value } }

    override suspend fun updateOlmSessions(
        senderKey: Key.Curve25519Key,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    ) = olmStore.updateOlmSessions(senderKey, updater)

    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) = olmStore.updateOutboundMegolmSession(roomId, updater)

    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) = olmStore.updateInboundMegolmSession(sessionId, roomId, updater)


    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        olmStore.getInboundMegolmSession(sessionId, roomId)

    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) = olmStore.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex, updater)

    override val olmAccount = olmStore.account
    override val olmPickleKey = requireNotNull(accountStore.olmPickleKey.value)

    override suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>> {
        userService.loadMembers(roomId)
        val members = roomStateStore.members(roomId, Membership.JOIN, Membership.INVITE)
        keyStore.waitForUpdateOutdatedKey(members)
        return members.mapNotNull { userId ->
            keyStore.getDeviceKeys(userId)?.let { userId to it.keys }
        }.toMap()
    }

    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? =
        roomStore.get(roomId).value?.encryptionAlgorithm
}