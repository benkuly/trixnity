package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key

data class UpdateResult<N, R>(
    val newValue: N,
    val resultValue: R
)

typealias ResultUpdater<N, R> = suspend (N) -> UpdateResult<N, R>

interface OlmMachineStore {
    suspend fun getCurve25519Key(userId: UserId, deviceId: String): Curve25519Key?
    suspend fun getEd25519Key(userId: UserId, deviceId: String): Ed25519Key?

    suspend fun findDeviceKeys(userId: UserId, senderKey: Curve25519Key): DeviceKeys?

    suspend fun <T> updateOlmSessions(
        senderKey: Curve25519Key,
        resultUpdater: ResultUpdater<Set<StoredOlmSession>?, T>
    ): T

    suspend fun <T> updateOutboundMegolmSession(
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredOutboundMegolmSession?, T>
    ): T

    suspend fun <T> updateInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
        resultUpdater: ResultUpdater<StoredInboundMegolmSession?, T>
    ): T

    suspend fun getInboundMegolmSession(
        sessionId: String,
        roomId: RoomId,
    ): StoredInboundMegolmSession?

    suspend fun <T> updateInboundMegolmMessageIndex(
        sessionId: String,
        roomId: RoomId,
        messageIndex: Long,
        resultUpdater: ResultUpdater<StoredInboundMegolmMessageIndex?, T>
    ): T

    suspend fun <T> updateOlmAccount(resultUpdater: ResultUpdater<String?, T>): T
    suspend fun getOlmAccount(): String?

    suspend fun getMembers(roomId: RoomId): Map<UserId, Set<String>>?

    suspend fun getRoomEncryptionAlgorithm(roomId: RoomId): EncryptionAlgorithm?
}