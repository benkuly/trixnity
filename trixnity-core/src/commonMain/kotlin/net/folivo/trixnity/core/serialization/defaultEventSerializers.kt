package net.folivo.trixnity.core.serialization

import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.serialization.EventSerializerDescriptor.Companion.of

val defaultRoomEventSerializers: Set<EventSerializerDescriptor<out RoomEvent<*>, *>> = setOf(
    of("m.room.message", MessageEvent.serializer()),
    of("m.room.redaction", RedactionEvent.serializer())
)

val defaultStateEventSerializers: Set<EventSerializerDescriptor<out StateEvent<*>, *>> = setOf(
    of("m.room.avatar", AvatarEvent.serializer()),
    of("m.room.canonical_alias", CanonicalAliasEvent.serializer()),
    of("m.room.create", CreateEvent.serializer()),
    of("m.room.join_rules", JoinRulesEvent.serializer()),
    of("m.room.member", MemberEvent.serializer()),
    of("m.room.name", NameEvent.serializer()),
    of("m.room.pinned_events", PinnedEventsEvent.serializer()),
    of("m.room.power_levels", PowerLevelsEvent.serializer()),
    of("m.room.topic", TopicEvent.serializer())
)