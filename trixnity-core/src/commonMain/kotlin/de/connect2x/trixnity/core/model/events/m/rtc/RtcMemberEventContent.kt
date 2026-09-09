package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@MSC4143
@MSC4354
@Serializable(with = RtcMemberEventContent.Serializer::class)
sealed interface RtcMemberEventContent : StickyEventContent {
    val slotId: RtcSlotId
    val member: Member
    override val relatesTo: RelatesTo.Reference?

    @MSC4143
    @MSC4354
    @Serializable
    data class Join(
        @SerialName("slot_id") override val slotId: RtcSlotId,
        @SerialName("member") override val member: Member,
        @SerialName("application") val application: @Contextual RtcApplicationMember,
        @SerialName("transports") val transports: @Contextual RtcTransports? = null,
        @MSC4354
        @property:OptIn(ExperimentalSerializationApi::class)
        @JsonNames("sticky_key")
        @SerialName("msc4354_sticky_key")
        override val stickyKey: String,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo.Reference? = null,
    ) : RtcMemberEventContent {
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        override fun copyWith(relatesTo: RelatesTo?): MessageEventContent =
            copy(relatesTo = relatesTo as? RelatesTo.Reference)
    }

    @MSC4143
    @MSC4354
    @Serializable
    data class Leave(
        @SerialName("slot_id") override val slotId: RtcSlotId,
        @SerialName("member") override val member: Member,
        @SerialName("leave_reason") val reason: Reason? = null,
        @MSC4354
        @property:OptIn(ExperimentalSerializationApi::class)
        @JsonNames("sticky_key")
        @SerialName("msc4354_sticky_key")
        override val stickyKey: String,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo.Reference? = null,
    ) : RtcMemberEventContent {
        override val mentions: Mentions? = null
        override val externalUrl: String? = null

        override fun copyWith(relatesTo: RelatesTo?): MessageEventContent =
            copy(relatesTo = relatesTo as? RelatesTo.Reference)

        @MSC4143
        @Serializable
        data class Reason(@SerialName("code") val code: String, @SerialName("reason") val reason: String? = null)
    }

    @MSC4143 @Serializable data class Member(@SerialName("id") val id: RtcMemberId)

    @MSC4143
    @Serializable
    data class RtcTransports(
        @SerialName("published") val published: List<@Contextual RtcTransport>? = null,
        @SerialName("can_subscribe") val canSubscribe: List<String>? = null,
    )

    object Serializer : KSerializer<RtcMemberEventContent> {
        override val descriptor = buildClassSerialDescriptor("RtcMemberEventContent")

        override fun deserialize(decoder: Decoder): RtcMemberEventContent {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            return when (val membership = jsonObject["member"]?.jsonObject?.get("membership")?.jsonPrimitive?.content) {
                "join" -> decoder.json.decodeFromJsonElement(Join.serializer(), jsonObject)
                "leave" -> decoder.json.decodeFromJsonElement(Leave.serializer(), jsonObject)
                else -> throw SerializationException("unknown membership: $membership")
            }
        }

        override fun serialize(encoder: Encoder, value: RtcMemberEventContent) {
            require(encoder is JsonEncoder)
            val jsonObject =
                when (value) {
                    is Join -> encoder.json.encodeToJsonElement(Join.serializer(), value)
                    is Leave -> encoder.json.encodeToJsonElement(Leave.serializer(), value)
                }.jsonObject
            encoder.encodeJsonElement(
                JsonObject(
                    buildMap {
                        putAll(jsonObject)
                        put(
                            "member",
                            JsonObject(
                                buildMap {
                                    putAll(requireNotNull(jsonObject["member"]?.jsonObject))
                                    put(
                                        "membership",
                                        JsonPrimitive(
                                            when (value) {
                                                is Join -> "join"
                                                is Leave -> "leave"
                                            }
                                        ),
                                    )
                                }
                            ),
                        )
                    }
                )
            )
        }
    }
}
