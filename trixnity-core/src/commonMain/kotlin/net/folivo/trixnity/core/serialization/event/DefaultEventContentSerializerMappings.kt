package net.folivo.trixnity.core.serialization.event

import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping.Companion.of
import net.folivo.trixnity.core.serialization.m.room.encrypted.EncryptedEventContentSerializer
import net.folivo.trixnity.core.serialization.m.room.message.RoomMessageEventContentSerializer

object DefaultEventContentSerializerMappings : EventContentSerializerMappings {
    override val message: Set<EventContentSerializerMapping<out MessageEventContent>> = setOf(
        of("m.room.message", RoomMessageEventContentSerializer),
        of<RedactionEventContent>("m.room.redaction"),
        of("m.room.encrypted", EncryptedEventContentSerializer),
        of<StartEventContent>("m.key.verification.start"),
        of<ReadyEventContent>("m.key.verification.ready"),
        of<DoneEventContent>("m.key.verification.done"),
        of<CancelEventContent>("m.key.verification.cancel"),
        of<SasAcceptEventContent>("m.key.verification.accept"),
        of<SasKeyEventContent>("m.key.verification.key"),
        of<SasMacEventContent>("m.key.verification.mac")
    )
    override val state: Set<EventContentSerializerMapping<out StateEventContent>> = setOf(
        of<AvatarEventContent>("m.room.avatar"),
        of<CanonicalAliasEventContent>("m.room.canonical_alias"),
        of<CreateEventContent>("m.room.create"),
        of<JoinRulesEventContent>("m.room.join_rules"),
        of<MemberEventContent>("m.room.member"),
        of<NameEventContent>("m.room.name"),
        of<PinnedEventsEventContent>("m.room.pinned_events"),
        of<PowerLevelsEventContent>("m.room.power_levels"),
        of<TopicEventContent>("m.room.topic"),
        of<EncryptionEventContent>("m.room.encryption"),
    )
    override val ephemeral: Set<EventContentSerializerMapping<out EphemeralEventContent>> = setOf(
        of<PresenceEventContent>("m.presence"),
    )

    override val toDevice: Set<EventContentSerializerMapping<out ToDeviceEventContent>> = setOf(
        of("m.room.encrypted", EncryptedEventContentSerializer),
        of<RoomKeyEventContent>("m.room_key"),
        of<RoomKeyRequestEventContent>("m.room_key_request"),
        of<ForwardedRoomKeyEventContent>("m.forwarded_room_key"),
        of<DummyEventContent>("m.dummy"),
        of<RequestEventContent>("m.key.verification.request"),
        of<StartEventContent>("m.key.verification.start"),
        of<ReadyEventContent>("m.key.verification.ready"),
        of<DoneEventContent>("m.key.verification.done"),
        of<CancelEventContent>("m.key.verification.cancel"),
        of<SasAcceptEventContent>("m.key.verification.accept"),
        of<SasKeyEventContent>("m.key.verification.key"),
        of<SasMacEventContent>("m.key.verification.mac")
    )
    override val globalAccountData: Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>> = setOf(
        of<DirectEventContent>("m.direct")
    )
    override val roomAccountData: Set<EventContentSerializerMapping<out RoomAccountDataEventContent>> = setOf(
        of<FullyReadEventContent>("m.fully_read")
    )
}