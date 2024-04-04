package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key

interface OlmStore {
    suspend fun findCurve25519Key(userId: UserId, deviceId: String): Curve25519Key?
    suspend fun findEd25519Key(userId: UserId, deviceId: String): Ed25519Key?

    suspend fun findDeviceKeys(userId: UserId, senderKey: Curve25519Key): DeviceKeys?

    suspend fun updateOlmSessions(
        senderKey: Curve25519Key,
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

    suspend fun getDevices(roomId: RoomId, memberships: Set<Membership>): Map<UserId, Set<String>>?
    suspend fun getDevices(roomId: RoomId, userId: UserId): Set<String>?

    suspend fun getHistoryVisibility(roomId: RoomId): HistoryVisibilityEventContent.HistoryVisibility?

    suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm?
}