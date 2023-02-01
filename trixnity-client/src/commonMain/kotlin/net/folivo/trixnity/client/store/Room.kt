package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm

@Serializable
data class Room(
    val roomId: RoomId,
    val name: RoomDisplayName? = null,
    val avatarUrl: String? = null,
    val isDirect: Boolean = false,
    val lastEventId: EventId? = null, // this must only be changed by TimelineEventHandler!
    val lastRelevantEventId: EventId? = null,
    val unreadMessageCount: Long = 0,
    val encryptionAlgorithm: EncryptionAlgorithm? = null,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
    val previousRoomId: RoomId? = null,
    val nextRoomId: RoomId? = null,
)