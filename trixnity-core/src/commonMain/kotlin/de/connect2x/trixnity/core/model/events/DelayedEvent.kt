package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject

@MSC4140
sealed interface DelayedEvent<C : RoomEventContent> : Event<C> {
    val delayId: DelayId
    val roomId: RoomId
    val delayMs: Long
    val delayedSinceTs: Long
    val finalized: Finalized?

    @Serializable
    data class DelayedStateEvent<C : StateEventContent>(
        @SerialName("delay_id") override val delayId: DelayId,
        @SerialName("room_id") override val roomId: RoomId,
        @SerialName("state_key") val stateKey: String,
        @SerialName("delay_ms") override val delayMs: Long,
        @SerialName("delayed_since_ts") override val delayedSinceTs: Long,
        @SerialName("content") override val content: C,
        @SerialName("finalized") override val finalized: Finalized? = null,
    ) : DelayedEvent<C>

    @Serializable
    data class DelayedMessageEvent<C : MessageEventContent>(
        @SerialName("delay_id") override val delayId: DelayId,
        @SerialName("room_id") override val roomId: RoomId,
        @SerialName("delay_ms") override val delayMs: Long,
        @SerialName("delayed_since_ts") override val delayedSinceTs: Long,
        @SerialName("content") override val content: C,
        @SerialName("finalized") override val finalized: Finalized? = null,
    ) : DelayedEvent<C>

    @Serializable(with = Finalized.Serializer::class)
    sealed interface Finalized {
        val finalizedTs: Long

        @Serializable
        data class Success(
            @SerialName("finalised_ts") override val finalizedTs: Long,
            @SerialName("event_id") val eventId: EventId,
        ) : Finalized

        @Serializable
        data class Error(
            @SerialName("finalised_ts") override val finalizedTs: Long,
            @SerialName("error") val error: @Serializable(ErrorResponse.Serializer::class) ErrorResponse,
        ) : Finalized

        object Serializer : KSerializer<Finalized> {
            override val descriptor = buildClassSerialDescriptor("DelayedEvent.Finalized")

            override fun deserialize(decoder: Decoder): Finalized {
                require(decoder is JsonDecoder)
                val jsonObject = decoder.decodeJsonElement().jsonObject
                return if (jsonObject.containsKey("event_id")) {
                    decoder.decodeSerializableValue(Success.serializer())
                } else {
                    decoder.decodeSerializableValue(Error.serializer())
                }
            }

            override fun serialize(encoder: Encoder, value: Finalized) {
                when (value) {
                    is Success -> encoder.encodeSerializableValue(Success.serializer(), value)
                    is Error -> encoder.encodeSerializableValue(Error.serializer(), value)
                }
            }
        }
    }
}
