package de.connect2x.trixnity.crypto.olm

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class StoredOutboundMegolmSession(
    val roomId: RoomId,
    val createdAt: Instant = Clock.System.now(),
    val encryptedMessageCount: Long = 1,
    val newDevices: Map<UserId, Set<String>> = mapOf(),
    val pickled: String
)