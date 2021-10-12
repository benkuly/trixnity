package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

@Serializable
data class Room(
    val roomId: RoomId,
    val name: RoomDisplayName? = null,
    val lastEventAt: Instant,
    val lastEventId: EventId?,
    val unreadMessageCount: Int = 0,
    val encryptionAlgorithm: EncryptionAlgorithm? = null,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
)