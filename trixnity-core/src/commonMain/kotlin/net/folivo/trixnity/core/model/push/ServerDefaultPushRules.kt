package net.folivo.trixnity.core.model.push

import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#predefined-rules">matrix spec</a>
 */
sealed interface ServerDefaultPushRules {
    val rule: PushRule
    val id: String get() = rule.ruleId
    val kind: PushRuleKind

    companion object {
        fun all(userId: UserId): Set<ServerDefaultPushRules> =
            Override.all(userId) + Underride.all()
    }

    sealed interface Override : ServerDefaultPushRules {
        override val kind: PushRuleKind get() = PushRuleKind.OVERRIDE

        companion object {
            fun all(userId: UserId): Set<Override> = setOf(
                Master,
                SuppressNotice,
                InviteForMe(userId),
                MemberEvent,
                IsUserMention(userId),
                IsRoomMention,
                Tombstone,
                Reaction,
                ServerAcl
            )
        }

        object Master : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.master",
                default = true,
                enabled = false,
                conditions = setOf(),
                actions = setOf(),
            )
        }

        object SuppressNotice : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.suppress_notices",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "content.msgtype",
                        pattern = "m.notice",
                    )
                ),
                actions = setOf(),
            )
        }

        data class InviteForMe(val userId: UserId) : Override {
            companion object {
                const val id: String = ".m.rule.invite_for_me"
            }

            override val rule: PushRule = PushRule(
                ruleId = Companion.id,
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.member",
                    ),
                    PushCondition.EventMatch(
                        key = "content.membership",
                        pattern = "invite",
                    ),
                    PushCondition.EventMatch(
                        key = "state_key",
                        pattern = userId.full,
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetSoundTweak("default"),
                ),
            )
        }

        object MemberEvent : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.member_event",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.member",
                    ),
                ),
                actions = setOf(),
            )
        }

        data class IsUserMention(val userId: UserId) : Override {
            companion object {
                const val id: String = ".m.rule.is_user_mention"
            }

            override val rule: PushRule = PushRule(
                ruleId = Companion.id,
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventPropertyContains(
                        key = """content.m\\.mentions.user_ids""",
                        value = JsonPrimitive(userId.full),
                    ),
                ),
                actions = setOf(
                    PushAction.SetSoundTweak("default"),
                    PushAction.SetHighlightTweak(),
                ),
            )
        }

        object IsRoomMention : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.is_room_mention",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventPropertyIs(
                        key = """content.m\\.mentions.room""",
                        value = JsonPrimitive(true),
                    ),
                    PushCondition.SenderNotificationPermission(
                        key = "room"
                    )
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetHighlightTweak(),
                ),
            )
        }

        object Tombstone : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.tombstone",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.tombstone",
                    ),
                    PushCondition.EventMatch(
                        key = "event_match",
                        pattern = "",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetHighlightTweak(),
                ),
            )
        }

        object Reaction : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.reaction",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.reaction",
                    ),
                ),
                actions = setOf(),
            )
        }

        object ServerAcl : Override {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.room.server_acl",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.server_acl",
                    ),
                    PushCondition.EventMatch(
                        key = "state_key",
                        pattern = "",
                    ),
                ),
                actions = setOf(),
            )
        }
    }

    sealed interface Underride : ServerDefaultPushRules {
        override val kind: PushRuleKind get() = PushRuleKind.UNDERRIDE

        companion object {
            fun all(): Set<Underride> = setOf(
                Call,
                EncryptedRoomOneToOne,
                RoomOneToOne,
                Message,
                Encrypted
            )
        }

        object Call : Underride {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.call",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.call.invite",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetSoundTweak("ring")
                ),
            )
        }

        object EncryptedRoomOneToOne : Underride {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.encrypted_room_one_to_one",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.RoomMemberCount("2"),
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.encrypted",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetSoundTweak("default")
                ),
            )
        }

        object RoomOneToOne : Underride {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.room_one_to_one",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.RoomMemberCount("2"),
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.message",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                    PushAction.SetSoundTweak("default")
                ),
            )
        }

        object Message : Underride {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.message",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.message",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                ),
            )
        }

        object Encrypted : Underride {
            override val rule: PushRule = PushRule(
                ruleId = ".m.rule.encrypted",
                default = true,
                enabled = true,
                conditions = setOf(
                    PushCondition.EventMatch(
                        key = "type",
                        pattern = "m.room.encrypted",
                    ),
                ),
                actions = setOf(
                    PushAction.Notify,
                ),
            )
        }
    }
}