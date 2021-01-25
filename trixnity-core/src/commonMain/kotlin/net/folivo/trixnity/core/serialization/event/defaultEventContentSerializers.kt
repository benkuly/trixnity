package net.folivo.trixnity.core.serialization.event

import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping.Companion.of
import net.folivo.trixnity.core.serialization.m.room.message.MessageEventContentSerializer

val DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS: Set<EventContentSerializerMapping<out RoomEventContent>> = setOf(
    of("m.room.message", MessageEventContentSerializer),
    of("m.room.redaction", RedactionEventContent.serializer())
)

val DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS: Set<EventContentSerializerMapping<out StateEventContent>> = setOf(
    of("m.room.avatar", AvatarEventContent.serializer()),
    of("m.room.canonical_alias", CanonicalAliasEventContent.serializer()),
    of("m.room.create", CreateEventContent.serializer()),
    of("m.room.join_rules", JoinRulesEventContent.serializer()),
    of("m.room.member", MemberEventContent.serializer()),
    of("m.room.name", NameEventContent.serializer()),
    of("m.room.pinned_events", PinnedEventsEventContent.serializer()),
    of("m.room.power_levels", PowerLevelsEventContent.serializer()),
    of("m.room.topic", TopicEventContent.serializer())
)