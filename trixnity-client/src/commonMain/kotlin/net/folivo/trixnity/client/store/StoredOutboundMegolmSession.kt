package net.folivo.trixnity.client.store

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.MatrixId

data class StoredOutboundMegolmSession(
    val roomId: MatrixId.RoomId,
    val createdAt: Instant = Clock.System.now(),
    val encryptedMessageCount: Long = 1,
    val newDevices: Map<MatrixId.UserId, Set<String>> = mapOf(),
    val pickle: String
)