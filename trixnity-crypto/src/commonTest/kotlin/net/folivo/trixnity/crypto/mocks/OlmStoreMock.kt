package net.folivo.trixnity.crypto.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.crypto.olm.*

class OlmStoreMock : OlmStore {
    val curve25519Keys = mutableMapOf<Pair<UserId, String>, Key.Curve25519Key>()
    override suspend fun findCurve25519Key(userId: UserId, deviceId: String): Key.Curve25519Key? {
        return curve25519Keys[userId to deviceId]
    }

    val ed25519Keys = mutableMapOf<Pair<UserId, String>, Key.Ed25519Key>()
    override suspend fun findEd25519Key(userId: UserId, deviceId: String): Key.Ed25519Key? {
        return ed25519Keys[userId to deviceId]
    }

    val deviceKeys = mutableMapOf<Pair<UserId, Key.Curve25519Key>, DeviceKeys>()
    override suspend fun findDeviceKeys(userId: UserId, senderKey: Key.Curve25519Key): DeviceKeys? {
        return deviceKeys[userId to senderKey]
    }

    val olmSessions = mutableMapOf<Key.Curve25519Key, Set<StoredOlmSession>?>()
    override suspend fun updateOlmSessions(
        senderKey: Key.Curve25519Key,
        updater: suspend (Set<StoredOlmSession>?) -> (Set<StoredOlmSession>?)
    ) {
        olmSessions[senderKey] = updater(olmSessions[senderKey])
    }

    val outboundMegolmSession = mutableMapOf<RoomId, StoredOutboundMegolmSession?>()
    override suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    ) {
        outboundMegolmSession[roomId] = updater(outboundMegolmSession[roomId])
    }

    val inboundMegolmSession = mutableMapOf<Pair<String, RoomId>, StoredInboundMegolmSession?>()
    override suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    ) {
        inboundMegolmSession[sessionId to roomId] = updater(inboundMegolmSession[sessionId to roomId])
    }

    override suspend fun getInboundMegolmSession(sessionId: String, roomId: RoomId): StoredInboundMegolmSession? {
        return inboundMegolmSession[sessionId to roomId]
    }


    val inboundMegolmSessionIndex = mutableMapOf<Triple<String, RoomId, Long>, StoredInboundMegolmMessageIndex?>()
    override suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    ) {
        inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)] =
            updater(inboundMegolmSessionIndex[Triple(sessionId, roomId, messageIndex)])
    }


    val olmAccount: MutableStateFlow<String> = MutableStateFlow("")
    override suspend fun getOlmAccount(): String = olmAccount.value
    override suspend fun updateOlmAccount(updater: suspend (String) -> String) {
        olmAccount.update { updater(it) }
    }

    override suspend fun getOlmPickleKey(): String = ""
    val forgetFallbackKeyAfter: MutableStateFlow<Instant?> = MutableStateFlow(null)
    override suspend fun getForgetFallbackKeyAfter(): Flow<Instant?> = forgetFallbackKeyAfter
    override suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?) {
        forgetFallbackKeyAfter.update { updater(it) }
    }

    val devices = mutableMapOf<RoomId, Map<UserId, Set<String>>>()
    override suspend fun getDevices(roomId: RoomId, memberships: Set<Membership>): Map<UserId, Set<String>>? {
        return devices[roomId]
    }

    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? {
        return null
    }

    val roomEncryptionAlgorithm = mutableMapOf<RoomId, EncryptionAlgorithm?>()
    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? {
        return roomEncryptionAlgorithm[roomId]
    }
}