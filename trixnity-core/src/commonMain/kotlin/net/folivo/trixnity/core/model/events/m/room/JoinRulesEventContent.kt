package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent.AllowCondition.AllowConditionType
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent.JoinRule

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#mroomjoin_rules">matrix spec</a>
 */
@Serializable
data class JoinRulesEventContent(
    @SerialName("join_rule")
    val joinRule: JoinRule,
    @SerialName("allow")
    val allow: Set<AllowCondition>? = null
) : StateEventContent {
    @Serializable(with = JoinRuleSerializer::class)
    sealed interface JoinRule {
        abstract val name: String

        object Public : JoinRule {
            override val name = "public"
        }

        object Knock : JoinRule {
            override val name = "knock"
        }

        object Invite : JoinRule {
            override val name = "invite"
        }

        object Private : JoinRule {
            override val name = "private"
        }

        object Restricted : JoinRule {
            override val name = "restricted"
        }

        object KnockRestricted : JoinRule {
            override val name = "knock_restricted"
        }

        data class Unknown(override val name: String) : JoinRule
    }

    @Serializable
    data class AllowCondition(
        @SerialName("room_id")
        val roomId: RoomId,
        @SerialName("type")
        val type: AllowConditionType
    ) {
        @Serializable(with = AllowConditionTypeSerializer::class)
        sealed interface AllowConditionType {
            val name: String

            object RoomMembership : AllowConditionType {
                override val name = "m.room_membership"
            }

            data class Unknown(override val name: String) : AllowConditionType
        }
    }
}

object JoinRuleSerializer : KSerializer<JoinRule> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JoinRuleSerializer")

    override fun deserialize(decoder: Decoder): JoinRule {
        return when (val name = decoder.decodeString()) {
            JoinRule.Public.name -> JoinRule.Public
            JoinRule.Knock.name -> JoinRule.Knock
            JoinRule.Invite.name -> JoinRule.Invite
            JoinRule.Private.name -> JoinRule.Private
            JoinRule.Restricted.name -> JoinRule.Restricted
            JoinRule.KnockRestricted.name -> JoinRule.KnockRestricted
            else -> JoinRule.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: JoinRule) {
        encoder.encodeString(value.name)
    }
}

object AllowConditionTypeSerializer : KSerializer<AllowConditionType> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AllowConditionTypeSerializer")

    override fun deserialize(decoder: Decoder): AllowConditionType {
        return when (val name = decoder.decodeString()) {
            AllowConditionType.RoomMembership.name -> AllowConditionType.RoomMembership
            else -> AllowConditionType.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: AllowConditionType) {
        encoder.encodeString(value.name)
    }
}