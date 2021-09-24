package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

data class Room(
    val roomId: MatrixId.RoomId,
    val name: RoomDisplayName? = null,
    val lastEventAt: Instant,
    val lastEventId: EventId?,
    val unreadMessageCount: Int = 0,
    val encryptionAlgorithm: EncryptionAlgorithm? = null,
    val ownMembership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
)