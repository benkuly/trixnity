package net.folivo.trixnity.core.model.push

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#conditions-1">matrix spec</a>
 */
@Serializable(with = PushCondition.Serializer::class)
sealed interface PushCondition {
    @Serializable
    data class EventMatch(
        @SerialName("key")
        val key: String,
        @SerialName("pattern")
        val pattern: String
    ) : PushCondition

    @Serializable
    data class EventPropertyIs(
        @SerialName("key")
        val key: String,
        @SerialName("value")
        val value: JsonPrimitive
    ) : PushCondition

    @Serializable
    data class EventPropertyContains(
        @SerialName("key")
        val key: String,
        @SerialName("value")
        val value: JsonPrimitive
    ) : PushCondition

    @Deprecated("since Matrix 1.17")
    data object ContainsDisplayName : PushCondition

    @Serializable
    data class RoomMemberCount(
        @SerialName("is")
        val isCount: String
    ) : PushCondition

    @Serializable
    data class SenderNotificationPermission(
        @SerialName("key")
        val key: String
    ) : PushCondition

    data class Unknown(
        val raw: JsonObject
    ) : PushCondition

    object Serializer : KSerializer<PushCondition> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushCondition")

        override fun deserialize(decoder: Decoder): PushCondition {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            return try {
                @Suppress("DEPRECATION")
                when ((jsonObject["kind"] as? JsonPrimitive)?.contentOrNull) {
                    "event_match" -> decoder.json.decodeFromJsonElement<PushCondition.EventMatch>(jsonObject)
                    "event_property_is" -> decoder.json.decodeFromJsonElement<PushCondition.EventPropertyIs>(jsonObject)
                    "event_property_contains" ->
                        decoder.json.decodeFromJsonElement<PushCondition.EventPropertyContains>(jsonObject)

                    "room_member_count" -> decoder.json.decodeFromJsonElement<PushCondition.RoomMemberCount>(jsonObject)
                    "sender_notification_permission" ->
                        decoder.json.decodeFromJsonElement<PushCondition.SenderNotificationPermission>(jsonObject)

                    "contains_display_name" -> PushCondition.ContainsDisplayName
                    else -> PushCondition.Unknown(jsonObject)
                }
            } catch (exc: SerializationException) {
                PushCondition.Unknown(jsonObject)
            }
        }

        override fun serialize(encoder: Encoder, value: PushCondition) {
            require(encoder is JsonEncoder)
            @Suppress("DEPRECATION")
            val jsonObject = when (value) {
                is EventMatch -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(EventMatch.serializer(), "kind" to "event_match"),
                    value
                )

                is EventPropertyIs -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(EventPropertyIs.serializer(), "kind" to "event_property_is"),
                    value
                )

                is EventPropertyContains -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(
                        EventPropertyContains.serializer(),
                        "kind" to "event_property_contains"
                    ),
                    value
                )

                is RoomMemberCount -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(RoomMemberCount.serializer(), "kind" to "room_member_count"),
                    value
                )

                is SenderNotificationPermission -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(
                        SenderNotificationPermission.serializer(),
                        "kind" to "sender_notification_permission"
                    ),
                    value
                )

                is ContainsDisplayName -> JsonObject(mapOf("kind" to JsonPrimitive("contains_display_name")))
                is Unknown -> value.raw
            }
            encoder.encodeJsonElement(jsonObject)
        }
    }
}