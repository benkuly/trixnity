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
    val lastMessageEventAt: Instant? = null,
    val lastEventId: EventId? = null, // This may only be changed by RoomManager::setLastEventId !!!
    val lastMessageEventId: EventId? = null,
    val unreadMessageCount: Int = 0,
    val encryptionAlgorithm: EncryptionAlgorithm? = null,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
)