package net.folivo.trixnity.core.serialization.events

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

val DefaultEventContentSerializerMappings = createEventContentSerializerMappings {
    messageOf("m.room.message", RoomMessageEventContentSerializer)
    messageOf<ReactionEventContent>("m.reaction")
    messageOf<RedactionEventContent>("m.room.redaction")
    messageOf("m.room.encrypted", EncryptedMessageEventContentSerializer)
    messageOf<VerificationStartEventContent>("m.key.verification.start")
    messageOf<VerificationReadyEventContent>("m.key.verification.ready")
    messageOf<VerificationDoneEventContent>("m.key.verification.done")
    messageOf<VerificationCancelEventContent>("m.key.verification.cancel")
    messageOf<SasAcceptEventContent>("m.key.verification.accept")
    messageOf<SasKeyEventContent>("m.key.verification.key")
    messageOf<SasMacEventContent>("m.key.verification.mac")

    stateOf<AvatarEventContent>("m.room.avatar")
    stateOf<CanonicalAliasEventContent>("m.room.canonical_alias")
    stateOf<CreateEventContent>("m.room.create")
    stateOf<JoinRulesEventContent>("m.room.join_rules")
    stateOf<MemberEventContent>("m.room.member")
    stateOf<NameEventContent>("m.room.name")
    stateOf<PinnedEventsEventContent>("m.room.pinned_events")
    stateOf<PowerLevelsEventContent>("m.room.power_levels")
    stateOf<TopicEventContent>("m.room.topic")
    stateOf<EncryptionEventContent>("m.room.encryption")
    stateOf<HistoryVisibilityEventContent>("m.room.history_visibility")
    stateOf<ThirdPartyInviteEventContent>("m.room.third_party_invite")
    stateOf<GuestAccessEventContent>("m.room.guest_access")
    stateOf<ServerACLEventContent>("m.room.server_acl")
    stateOf<TombstoneEventContent>("m.room.tombstone")
    stateOf<UserRuleEventContent>("m.policy.rule.user")
    stateOf<RoomRuleEventContent>("m.policy.rule.room")
    stateOf<ServerRuleEventContent>("m.policy.rule.server")
    stateOf<ParentEventContent>("m.space.parent")
    stateOf<ChildEventContent>("m.space.child")

    ephemeralOf<PresenceEventContent>("m.presence")
    ephemeralOf<TypingEventContent>("m.typing")
    ephemeralOf<ReceiptEventContent>("m.receipt")

    toDeviceOf("m.room.encrypted", EncryptedToDeviceEventContentSerializer)
    toDeviceOf<RoomKeyEventContent>("m.room_key")
    toDeviceOf<RoomKeyRequestEventContent>("m.room_key_request")
    toDeviceOf<ForwardedRoomKeyEventContent>("m.forwarded_room_key")
    toDeviceOf<DummyEventContent>("m.dummy")
    toDeviceOf<VerificationRequestEventContent>("m.key.verification.request")
    toDeviceOf<VerificationStartEventContent>("m.key.verification.start")
    toDeviceOf<VerificationReadyEventContent>("m.key.verification.ready")
    toDeviceOf<VerificationDoneEventContent>("m.key.verification.done")
    toDeviceOf<VerificationCancelEventContent>("m.key.verification.cancel")
    toDeviceOf<SasAcceptEventContent>("m.key.verification.accept")
    toDeviceOf<SasKeyEventContent>("m.key.verification.key")
    toDeviceOf<SasMacEventContent>("m.key.verification.mac")
    toDeviceOf<SecretKeyRequestEventContent>("m.secret.request")
    toDeviceOf<SecretKeySendEventContent>("m.secret.send")

    globalAccountDataOf<IdentityServerEventContent>("m.identity_server")
    globalAccountDataOf<DirectEventContent>("m.direct")
    globalAccountDataOf<PushRulesEventContent>("m.push_rules")
    globalAccountDataOf<DefaultSecretKeyEventContent>("m.secret_storage.default_key")
    globalAccountDataOf<SecretKeyEventContent>("m.secret_storage.key.")
    globalAccountDataOf<MasterKeyEventContent>("m.cross_signing.master")
    globalAccountDataOf<SelfSigningKeyEventContent>("m.cross_signing.self_signing")
    globalAccountDataOf<UserSigningKeyEventContent>("m.cross_signing.user_signing")
    globalAccountDataOf<MegolmBackupV1EventContent>("m.megolm_backup.v1")
    globalAccountDataOf<IgnoredUserListEventContent>("m.ignored_user_list")

    roomAccountDataOf<FullyReadEventContent>("m.fully_read")
    roomAccountDataOf<TagEventContent>("m.tag")
}