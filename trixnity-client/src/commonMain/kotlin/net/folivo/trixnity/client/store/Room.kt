package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership

@Serializable
data class Room(
    val roomId: RoomId,
    val createEventContent: CreateEventContent? = null,
    val name: RoomDisplayName? = null,
    val avatarUrl: String? = null,
    val isDirect: Boolean = false,
    val lastEventId: EventId? = null, // this must only be changed by TimelineEventHandler!
    val lastRelevantEventId: EventId? = null,
    val lastRelevantEventTimestamp: Instant? = null,
    val unreadMessageCount: Long = 0,
    val encrypted: Boolean = false,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
    val nextRoomId: RoomId? = null,
)

val Room.previousRoomId: RoomId? get() = createEventContent?.predecessor?.roomId

val Room.type: CreateEventContent.RoomType? get() = createEventContent?.type

@Deprecated("use sender of CreateEventContent instead")
val Room.creator: UserId? get() = createEventContent?.creator
val Room.federate: Boolean? get() = createEventContent?.federate
val Room.version: String? get() = createEventContent?.roomVersion