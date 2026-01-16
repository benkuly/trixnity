package de.connect2x.trixnity.crypto.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.olm.*
import kotlin.time.Instant

class OlmStoreMock : OlmStore {
    val devices: MutableMap<UserId, Map<String, DeviceKeys>> = mutableMapOf()
    override suspend fun getDeviceKeys(userId: UserId): Map<String, DeviceKeys>? =
        devices[userId]

    val roomMembers = mutableMapOf<RoomId, Set<UserId>>()
    override suspend fun getMembers(roomId: RoomId, memberships: Set<Membership>): Set<UserId> =
        roomMembers[roomId].orEmpty()

    val deviceTrustLevels: MutableMap<UserId, Map<String, DeviceTrustLevel>> = mutableMapOf()
    override suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel? =
        deviceTrustLevels[userId]?.get(deviceId)


    val olmSessions = mutableMapOf<Curve25519KeyValue, Set<StoredOlmSession>?>()
    override suspend fun updateOlmSessions(
        senderKeyValue: Curve25519KeyValue,
        updater: suspend (Set<StoredOlmSession>?) -> (Set<StoredOlmSession>?)
    ) {
        olmSessions[senderKeyValue] = updater(olmSessions[senderKeyValue])
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

    override suspend fun getOlmPickleKey(): String? = null
    val forgetFallbackKeyAfter: MutableStateFlow<Instant?> = MutableStateFlow(null)
    override suspend fun getForgetFallbackKeyAfter(): Flow<Instant?> = forgetFallbackKeyAfter
    override suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?) {
        forgetFallbackKeyAfter.update { updater(it) }
    }

    var historyVisibility: HistoryVisibilityEventContent.HistoryVisibility? = null
    override suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility? {
        return historyVisibility
    }

    val roomEncryptionAlgorithm = mutableMapOf<RoomId, EncryptionAlgorithm?>()
    override suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm? {
        return roomEncryptionAlgorithm[roomId]
    }
}