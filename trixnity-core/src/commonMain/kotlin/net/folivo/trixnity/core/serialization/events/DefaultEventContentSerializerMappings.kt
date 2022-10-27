package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.policy.RoomRuleEventContent
import net.folivo.trixnity.core.model.events.m.policy.ServerRuleEventContent
import net.folivo.trixnity.core.model.events.m.policy.UserRuleEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.events.m.space.ParentEventContent
import net.folivo.trixnity.core.serialization.events.SerializerMapping.Companion.of

object DefaultEventContentSerializerMappings : EventContentSerializerMappings {
    override val message: Set<SerializerMapping<out MessageEventContent>> = setOf(
        of("m.room.message", RoomMessageEventContentSerializer),
        of<RedactionEventContent>("m.room.redaction"),
        of("m.room.encrypted", EncryptedEventContentSerializer),
        of<VerificationStartEventContent>("m.key.verification.start"),
        of<VerificationReadyEventContent>("m.key.verification.ready"),
        of<VerificationDoneEventContent>("m.key.verification.done"),
        of<VerificationCancelEventContent>("m.key.verification.cancel"),
        of<SasAcceptEventContent>("m.key.verification.accept"),
        of<SasKeyEventContent>("m.key.verification.key"),
        of<SasMacEventContent>("m.key.verification.mac"),
    )
    override val state: Set<SerializerMapping<out StateEventContent>> = setOf(
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
        of<HistoryVisibilityEventContent>("m.room.history_visibility"),
        of<ThirdPartyInviteEventContent>("m.room.third_party_invite"),
        of<GuestAccessEventContent>("m.room.guest_access"),
        of<ServerACLEventContent>("m.room.server_acl"),
        of<TombstoneEventContent>("m.room.tombstone"),
        of<UserRuleEventContent>("m.policy.rule.user"),
        of<RoomRuleEventContent>("m.policy.rule.room"),
        of<ServerRuleEventContent>("m.policy.rule.server"),
        of<ParentEventContent>("m.space.parent"),
        of<ChildEventContent>("m.space.child"),
    )
    override val ephemeral: Set<SerializerMapping<out EphemeralEventContent>> = setOf(
        of<PresenceEventContent>("m.presence"),
        of<TypingEventContent>("m.typing"),
        of<ReceiptEventContent>("m.receipt"),
    )
    override val ephemeralDataUnit: Set<SerializerMapping<out EphemeralDataUnitContent>> = setOf()

    override val toDevice: Set<SerializerMapping<out ToDeviceEventContent>> = setOf(
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
        of<SecretKeySendEventContent>("m.secret.send"),
    )
    override val globalAccountData: Set<SerializerMapping<out GlobalAccountDataEventContent>> = setOf(
        of<IdentityServerEventContent>("m.identity_server"),
        of<DirectEventContent>("m.direct"),
        of<PushRulesEventContent>("m.push_rules"),
        of<DefaultSecretKeyEventContent>("m.secret_storage.default_key"),
        of<SecretKeyEventContent>("m.secret_storage.key."),
        of<MasterKeyEventContent>("m.cross_signing.master"),
        of<SelfSigningKeyEventContent>("m.cross_signing.self_signing"),
        of<UserSigningKeyEventContent>("m.cross_signing.user_signing"),
        of<MegolmBackupV1EventContent>("m.megolm_backup.v1"),
        of<IgnoredUserListEventContent>("m.ignored_user_list"),
    )
    override val roomAccountData: Set<SerializerMapping<out RoomAccountDataEventContent>> = setOf(
        of<FullyReadEventContent>("m.fully_read"),
        of<TagEventContent>("m.tag"),
    )
}