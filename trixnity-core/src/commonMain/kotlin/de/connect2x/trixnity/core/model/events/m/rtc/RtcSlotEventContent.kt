package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.StateEventContent
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@MSC4143
@Serializable(with = RtcSlotEventContent.Serializer::class)
sealed interface RtcSlotEventContent : StateEventContent {
    val application: RtcApplicationSlot?
    val encryption: RtcEncryption?

    @MSC4143
    @Serializable
    data class Open(
        @SerialName("application") override val application: @Contextual RtcApplicationSlot,
        @SerialName("encryption") override val encryption: @Contextual RtcEncryption? = null,
    ) : RtcSlotEventContent {
        override val externalUrl: String? = null
    }

    @Serializable
    data class Closed(
        @SerialName("application") override val application: @Contextual RtcApplicationSlot? = null,
        @SerialName("encryption") override val encryption: @Contextual RtcEncryption? = null,
    ) : RtcSlotEventContent {
        override val externalUrl: String? = null
    }

    object Serializer : KSerializer<RtcSlotEventContent> {

        override val descriptor = buildClassSerialDescriptor("RtcSlotEventContent")

        override fun deserialize(decoder: Decoder): RtcSlotEventContent {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val status = jsonObject["status"]?.jsonPrimitive?.contentOrNull
            requireNotNull(status)
            return when (status) {
                "open" -> decoder.json.decodeFromJsonElement(Open.serializer(), jsonObject)
                "closed" -> decoder.json.decodeFromJsonElement(Closed.serializer(), jsonObject)
                else -> throw SerializationException("unknown status: $status")
            }
        }

        override fun serialize(encoder: Encoder, value: RtcSlotEventContent) {
            require(encoder is JsonEncoder)
            val jsonObject =
                when (value) {
                    is Open -> encoder.json.encodeToJsonElement(Open.serializer(), value).jsonObject
                    is Closed -> encoder.json.encodeToJsonElement(Closed.serializer(), value).jsonObject
                }
            encoder.encodeJsonElement(
                JsonObject(
                    buildMap {
                        putAll(jsonObject)
                        put(
                            "status",
                            JsonPrimitive(
                                when (value) {
                                    is Open -> "open"
                                    is Closed -> "closed"
                                }
                            ),
                        )
                    }
                )
            )
        }
    }
}
