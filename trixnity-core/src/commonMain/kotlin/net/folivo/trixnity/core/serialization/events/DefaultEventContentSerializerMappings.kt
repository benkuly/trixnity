package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMapping.Companion.of

object DefaultEventContentSerializerMappings : EventContentSerializerMappings {
    override val message: Set<EventContentSerializerMapping<out MessageEventContent>> = setOf(
        of("m.room.message", RoomMessageEventContentSerializer),
        of<RedactionEventContent>("m.room.redaction"),
        of("m.room.encrypted", EncryptedEventContentSerializer),
        of<VerificationStartEventContent>("m.key.verification.start"),
        of<VerificationReadyEventContent>("m.key.verification.ready"),
        of<VerificationDoneEventContent>("m.key.verification.done"),
        of<VerificationCancelEventContent>("m.key.verification.cancel"),
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
        of<TypingEventContent>("m.typing"),
        of<ReceiptEventContent>("m.receipt"),
    )

    override val toDevice: Set<EventContentSerializerMapping<out ToDeviceEventContent>> = setOf(
        of("m.room.encrypted", EncryptedEventContentSerializer),
        of<RoomKeyEventContent>("m.room_key"),
        of<RoomKeyRequestEventContent>("m.room_key_request"),
        of<ForwardedRoomKeyEventContent>("m.forwarded_room_key"),
        of<DummyEventContent>("m.dummy"),
        of<VerificationRequestEventContent>("m.key.verification.request"),
        of<VerificationStartEventContent>("m.key.verification.start"),
        of<VerificationReadyEventContent>("m.key.verification.ready"),
        of<VerificationDoneEventContent>("m.key.verification.done"),
        of<VerificationCancelEventContent>("m.key.verification.cancel"),
        of<SasAcceptEventContent>("m.key.verification.accept"),
        of<SasKeyEventContent>("m.key.verification.key"),
        of<SasMacEventContent>("m.key.verification.mac"),
        of<SecretKeyRequestEventContent>("m.secret.request"),
        of<SecretKeySendEventContent>("m.secret.send")
    )
    override val globalAccountData: Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>> = setOf(
        of<DirectEventContent>("m.direct"),
        of<DefaultSecretKeyEventContent>("m.secret_storage.default_key"),
        of<SecretKeyEventContent>("m.secret_storage.key."),
        of<MasterKeyEventContent>("m.cross_signing.master"),
        of<SelfSigningKeyEventContent>("m.cross_signing.self_signing"),
        of<UserSigningKeyEventContent>("m.cross_signing.user_signing"),
        of<MegolmBackupV1EventContent>("m.megolm_backup.v1"),
    )
    override val roomAccountData: Set<EventContentSerializerMapping<out RoomAccountDataEventContent>> = setOf(
        of<FullyReadEventContent>("m.fully_read")
    )
}