package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

@Serializable
data class Room(
    val roomId: RoomId,
    val name: RoomDisplayName? = null,
    val avatarUrl: String? = null,
    val isDirect: Boolean = false,
    val lastMessageEventAt: Instant? = null,
    val lastEventId: EventId? = null, // This may only be changed by RoomManager::setLastEventId !!!
    val lastMessageEventId: EventId? = null,
    val unreadMessageCount: Int = 0,
    val encryptionAlgorithm: EncryptionAlgorithm? = null,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
)