package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import kotlin.time.Instant

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
    val encrypted: Boolean = false,
    val membership: Membership = Membership.JOIN,
    val membersLoaded: Boolean = false,
    val nextRoomId: RoomId? = null,
)

val Room.previousRoomId: RoomId? get() = createEventContent?.predecessor?.roomId
val Room.type: CreateEventContent.RoomType? get() = createEventContent?.type
val Room.federate: Boolean? get() = createEventContent?.federate
val Room.version: String? get() = createEventContent?.roomVersion
val Room.joinedMemberCount: Long? get() = name?.summary?.joinedMemberCount
val Room.invitedMemberCount: Long? get() = name?.summary?.invitedMemberCount