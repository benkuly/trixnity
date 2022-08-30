package net.folivo.trixnity.crypto.olm

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
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

    val olmAccount: MutableStateFlow<String?>
    val olmPickleKey: String

    suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>>?

    suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm?
}