package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomjoin_rules">matrix spec</a>
 */
@Serializable
data class JoinRulesEventContent(
    @SerialName("join_rule")
    val joinRule: JoinRule,
    @SerialName("allow")
    val allow: Set<AllowCondition>? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable(with = JoinRule.Serializer::class)
    sealed interface JoinRule {
        abstract val name: String

        data object Public : JoinRule {
            override val name = "public"
        }

        data object Knock : JoinRule {
            override val name = "knock"
        }

        data object Invite : JoinRule {
            override val name = "invite"
        }

        data object Private : JoinRule {
            override val name = "private"
        }

        data object Restricted : JoinRule {
            override val name = "restricted"
        }

        data object KnockRestricted : JoinRule {
            override val name = "knock_restricted"
        }

        data class Unknown(override val name: String) : JoinRule

        object Serializer : KSerializer<JoinRule> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("JoinRule")

            override fun deserialize(decoder: Decoder): JoinRule {
                return when (val name = decoder.decodeString()) {
                    Public.name -> Public
                    Knock.name -> Knock
                    Invite.name -> Invite
                    Private.name -> Private
                    Restricted.name -> Restricted
                    KnockRestricted.name -> KnockRestricted
                    else -> Unknown(name)
                }
            }

            override fun serialize(encoder: Encoder, value: JoinRule) {
                encoder.encodeString(value.name)
            }
        }
    }

    @Serializable
    data class AllowCondition(
        @SerialName("room_id")
        val roomId: RoomId,
        @SerialName("type")
        val type: AllowConditionType
    ) {
        @Serializable(with = AllowConditionType.Serializer::class)
        sealed interface AllowConditionType {
            val name: String

            data object RoomMembership : AllowConditionType {
                override val name = "m.room_membership"
            }

            data class Unknown(override val name: String) : AllowConditionType

            object Serializer : KSerializer<AllowConditionType> {
                override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AllowConditionType")

                override fun deserialize(decoder: Decoder): AllowConditionType {
                    return when (val name = decoder.decodeString()) {
                        RoomMembership.name -> RoomMembership
                        else -> Unknown(name)
                    }
                }

                override fun serialize(encoder: Encoder, value: AllowConditionType) {
                    encoder.encodeString(value.name)
                }
            }
        }
    }
}