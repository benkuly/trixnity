package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys
import net.folivo.trixnity.crypto.key.DeviceTrustLevel

interface OlmStore {
    suspend fun getDeviceKey(userId: UserId, deviceId: String): SignedDeviceKeys?
    suspend fun getDeviceKeys(userId: UserId): Map<String, SignedDeviceKeys>?
    suspend fun getDeviceKeys(roomId: RoomId, memberships: Set<Membership>): Map<UserId, Map<String, SignedDeviceKeys>>
    suspend fun getTrustLevel(userId: UserId, deviceId: String): DeviceTrustLevel?

    suspend fun updateOlmSessions(
        senderKeyValue: Curve25519KeyValue,
        updater: suspend (Set<StoredOlmSession>?) -> Set<StoredOlmSession>?
    )

    suspend fun updateOutboundMegolmSession(
        roomId: RoomId,
        updater: suspend (StoredOutboundMegolmSession?) -> StoredOutboundMegolmSession?
    )

    suspend fun updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        updater: suspend (StoredInboundMegolmSession?) -> StoredInboundMegolmSession?
    )

    suspend fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): StoredInboundMegolmSession?

    suspend fun updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        updater: suspend (StoredInboundMegolmMessageIndex?) -> StoredInboundMegolmMessageIndex?
    )

    suspend fun getOlmAccount(): String
    suspend fun updateOlmAccount(updater: suspend (String) -> String)
    suspend fun getOlmPickleKey(): String
    suspend fun getForgetFallbackKeyAfter(): Flow<Instant?>
    suspend fun updateForgetFallbackKeyAfter(updater: suspend (Instant?) -> Instant?)

    suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility?

    suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm?
}

suspend fun OlmStore.findDeviceKeys(userId: UserId, senderKeyValue: Curve25519KeyValue): DeviceKeys? =
    getDeviceKeys(userId)?.values?.map { it.signed }
        ?.find { it.keys.keys.any { key -> key.value == senderKeyValue } }