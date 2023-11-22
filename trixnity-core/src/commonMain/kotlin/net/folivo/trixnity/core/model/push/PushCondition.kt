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
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#conditions-1">matrix spec</a>
 */
@Serializable(with = PushConditionSerializer::class)
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
}

object PushConditionSerializer : KSerializer<PushCondition> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushConditionSerializer")

    override fun deserialize(decoder: Decoder): PushCondition {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return try {
            when ((jsonObject["kind"] as? JsonPrimitive)?.content) {
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
        val jsonObject = when (value) {
            is PushCondition.EventMatch -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(PushCondition.EventMatch.serializer(), "kind" to "event_match"),
                value
            )

            is PushCondition.EventPropertyIs -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(PushCondition.EventPropertyIs.serializer(), "kind" to "event_property_is"),
                value
            )

            is PushCondition.EventPropertyContains -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    PushCondition.EventPropertyContains.serializer(),
                    "kind" to "event_property_contains"
                ),
                value
            )

            is PushCondition.RoomMemberCount -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(PushCondition.RoomMemberCount.serializer(), "kind" to "room_member_count"),
                value
            )

            is PushCondition.SenderNotificationPermission -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    PushCondition.SenderNotificationPermission.serializer(),
                    "kind" to "sender_notification_permission"
                ),
                value
            )

            is PushCondition.ContainsDisplayName -> JsonObject(mapOf("kind" to JsonPrimitive("contains_display_name")))
            is PushCondition.Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonObject)
    }
}