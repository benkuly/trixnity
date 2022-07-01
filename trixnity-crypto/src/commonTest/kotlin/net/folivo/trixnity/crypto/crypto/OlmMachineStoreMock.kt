package net.folivo.trixnity.crypto.crypto

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.*

class OlmMachineStoreMock : OlmMachineStore {
    val curve25519Keys = mutableMapOf<Pair<UserId, String>, Key.Curve25519Key>()
    override suspend fun getCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? {
        return curve25519Keys[userId to deviceId]
    }

    val ed25519Keys = mutableMapOf<Pair<UserId, String>, Key.Ed25519Key>()
    override suspend fun getEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? {
        return ed25519Keys[userId to deviceId]
    }

    val deviceKeys = mutableMapOf<Pair<UserId, Key.Curve25519Key>, DeviceKeys>()
    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? {
        return deviceKeys[userId to senderKey]
    }

    val olmSessions = mutableMapOf<Key.Curve25519Key, Set<StoredOlmSession>?>()
    override suspend fun <T> updateOlmSessions(
        senderKey: Key.Curve25519Key,
        resultUpdater: ResultUpdater<Set<StoredOlmSession>?, T>
    ): T {
        val result = resultUpdater(olmSessions[senderKey])
        olmSessions[senderKey] = result.newValue
        return result.resultValue
    }

    val outboundMegolmSession = mutableMapOf<RoomId, StoredOutboundMegolmSession?>()
    override suspend fun <T> updateOutboundMegolmSession(
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredOutboundMegolmSession?, T>
    ): T {
        val result = resultUpdater(outboundMegolmSession[roomId])
        outboundMegolmSession[roomId] = result.newValue
        return result.resultValue
    }

    val inboundMegolmSession = mutableMapOf<Pair<String, RoomId>, StoredInboundMegolmSession?>()
    override suspend fun <T> updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredInboundMegolmSession?, T>
    ): T {
        val result = resultUpdater(inboundMegolmSession[sessionId to roomId])
        inboundMegolmSession[sessionId to roomId] = result.newValue
        return result.resultValue
    }

    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? {
        return inboundMegolmSession[sessionId to roomId]
    }


    val inboundMegolmSessionIndex = mutableMapOf<Triple<String, RoomId, Long>, StoredInboundMegolmMessageIndex?>()
    override suspend fun <T> updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        resultUpdater: ResultUpdater<StoredInboundMegolmMessageIndex?, T>
    ): T {
        val result = resultUpdater(inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)])
        inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)] = result.newValue
        return result.resultValue
    }


    var olmAccount: String? = null
    override suspend fun <T> updateOlmAccount(resultUpdater: ResultUpdater<String?, T>): T {
        val result = resultUpdater(olmAccount)
        olmAccount = result.newValue
        return result.resultValue
    }

    override suspend fun getOlmAccount(): String? = olmAccount

    val members = mutableMapOf<RoomId, Map<UserId, Set<String>>>()
    override suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>>? {
        return members[roomId]
    }

    val roomEncryptionAlgorithm = mutableMapOf<RoomId, EncryptionAlgorithm?>()
    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? {
        return roomEncryptionAlgorithm[roomId]
    }
}