package net.folivo.trixnity.client.store

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
data class StoredOutboundMegolmSession(
    val roomId: RoomId,
    val createdAt: Instant = Clock.System.now(),
    val encryptedMessageCount: Long = 1,
    val newDevices: Map<UserId, Set<String>> = mapOf(),
    val pickled: String
)