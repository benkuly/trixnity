package net.folivo.trixnity.client.store

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.UserId

@Serializable
data class StoredOutboundMegolmSession(
    val roomId: MatrixId.RoomId,
    val createdAt: Instant = Clock.System.now(),
    val encryptedMessageCount: Long = 1,
    val newDevices: Map<UserId, Set<String>> = mapOf(),
    val pickle: String
)