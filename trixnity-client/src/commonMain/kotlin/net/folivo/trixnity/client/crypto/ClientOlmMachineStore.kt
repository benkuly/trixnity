package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.key.get
import net.folivo.trixnity.client.key.getDeviceKey
import net.folivo.trixnity.client.key.waitForUpdateOutdatedKey
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.members
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.*

class UpdateToResultUpdate<V, T>(private val resultUpdater: ResultUpdater<V, T>) {
    private var internalReturnValue: T? = null
    suspend operator fun invoke(updater: V): V {
        val result = resultUpdater(updater)
        internalReturnValue = result.resultValue
        return result.newValue
    }

    val returnValue = requireNotNull(internalReturnValue)
}

class ClientOlmMachineStore(private val store: Store, private val userService: UserService) : OlmMachineStore {
    override suspend fun getCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? =
        store.keys.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun getEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? =
        store.keys.getDeviceKey(userId, deviceId)?.value?.get()

    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? =
        store.keys.getDeviceKeys(userId)?.values?.map { it.value.signed }
            ?.find { it.keys.keys.any { key -> key.value == senderKey.value } }

    override suspend fun <T> updateOlmSessions(
        senderKey: Key.Curve25519Key,
        resultUpdater: ResultUpdater<Set<StoredOlmSession>?, T>
    ): T {
        val updateToResultUpdate = UpdateToResultUpdate(resultUpdater)
        store.olm.updateOlmSessions(senderKey) { updateToResultUpdate(it) }
        return updateToResultUpdate.returnValue
    }

    override suspend fun <T> updateOutboundMegolmSession(
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredOutboundMegolmSession?, T>
    ): T {
        val updateToResultUpdate = UpdateToResultUpdate(resultUpdater)
        store.olm.updateOutboundMegolmSession(roomId) { updateToResultUpdate(it) }
        return updateToResultUpdate.returnValue
    }

    override suspend fun <T> updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredInboundMegolmSession?, T>
    ): T {
        val updateToResultUpdate = UpdateToResultUpdate(resultUpdater)
        store.olm.updateInboundMegolmSession(sessionId, roomId) { updateToResultUpdate(it) }
        return updateToResultUpdate.returnValue
    }

    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? =
        store.olm.getInboundMegolmSession(sessionId, roomId)

    override suspend fun <T> updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        resultUpdater: ResultUpdater<StoredInboundMegolmMessageIndex?, T>
    ): T {
        val updateToResultUpdate = UpdateToResultUpdate(resultUpdater)
        store.olm.updateInboundMegolmMessageIndex(sessionId, roomId, messageIndex) { updateToResultUpdate(it) }
        return updateToResultUpdate.returnValue
    }

    override suspend fun <T> updateOlmAccount(resultUpdater: ResultUpdater<String?, T>): T {
        val updateToResultUpdate = UpdateToResultUpdate(resultUpdater)
        store.olm.account.update { updateToResultUpdate(it) }
        return updateToResultUpdate.returnValue
    }

    override suspend fun getOlmAccount(): String? = store.olm.account.value

    override suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>> { // FIXME test
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