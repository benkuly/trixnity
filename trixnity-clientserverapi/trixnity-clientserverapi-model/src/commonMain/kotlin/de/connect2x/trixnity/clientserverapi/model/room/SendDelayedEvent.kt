package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.contentSerializer
import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject

@MSC4140
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc4140/rooms/{roomId}/delayed_event/{type}/{txnId}")
@HttpMethod(PUT)
data class SendDelayedEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("txnId") val txnId: String,
    @SerialName("ts") val ts: Long? = null,
) : MatrixEndpoint<SendDelayedEvent.Request, SendDelayedEvent.Response> {
    sealed interface Request {
        val content: RoomEventContent
        val delayMs: Long

        data class State(override val content: StateEventContent, override val delayMs: Long, val stateKey: String) :
            Request {
            @Serializable
            data class SerializableState<C : StateEventContent>(
                @SerialName("content") val content: C,
                @SerialName("delay_ms") val delayMs: Long,
                @SerialName("state_key") val stateKey: String,
            )

            class Serializer(contentSerializer: KSerializer<StateEventContent>) : KSerializer<State> {
                val delegateSerializer = SerializableState.serializer(contentSerializer)
                override val descriptor =
                    SerialDescriptor("SendDelayedEvent.Request.State", delegateSerializer.descriptor)

                override fun deserialize(decoder: Decoder): State {
                    val delegate = decoder.decodeSerializableValue(delegateSerializer)
                    return State(delayMs = delegate.delayMs, content = delegate.content, stateKey = delegate.stateKey)
                }

                override fun serialize(encoder: Encoder, value: State) {
                    encoder.encodeSerializableValue(
                        delegateSerializer,
                        SerializableState(delayMs = value.delayMs, content = value.content, stateKey = value.stateKey),
                    )
                }
            }
        }

        data class Message(
            @SerialName("content") override val content: MessageEventContent,
            @SerialName("delay_ms") override val delayMs: Long,
        ) : Request {
            @Serializable
            data class SerializableMessage<C : MessageEventContent>(
                @SerialName("content") val content: C,
                @SerialName("delay_ms") val delayMs: Long,
            )

            class Serializer(contentSerializer: KSerializer<MessageEventContent>) : KSerializer<Message> {
                val delegateSerializer = SerializableMessage.serializer(contentSerializer)
                override val descriptor =
                    SerialDescriptor("SendDelayedEvent.Request.Message", delegateSerializer.descriptor)

                override fun deserialize(decoder: Decoder): Message {
                    val delegate = decoder.decodeSerializableValue(delegateSerializer)
                    return Message(delayMs = delegate.delayMs, content = delegate.content)
                }

                override fun serialize(encoder: Encoder, value: Message) {
                    encoder.encodeSerializableValue(
                        delegateSerializer,
                        SerializableMessage(delayMs = value.delayMs, content = value.content),
                    )
                }
            }
        }

        class Serializer(
            private val type: String,
            private val givenValue: Request?,
            private val mappings: EventContentSerializerMappings,
        ) : KSerializer<Request> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SendDelayedEvent.Request")

            override fun deserialize(decoder: Decoder): Request {
                require(decoder is JsonDecoder)
                val jsonObject = decoder.decodeJsonElement().jsonObject
                return if (jsonObject.containsKey("state_key")) {
                    require(givenValue is State?)
                    decoder.json.decodeFromJsonElement(
                        State.Serializer(mappings.state.contentSerializer(type, givenValue?.content)),
                        jsonObject,
                    )
                } else {
                    require(givenValue is Message?)
                    decoder.json.decodeFromJsonElement(
                        Message.Serializer(mappings.message.contentSerializer(type, givenValue?.content)),
                        jsonObject,
                    )
                }
            }

            override fun serialize(encoder: Encoder, value: Request) {
                when (value) {
                    is State ->
                        encoder.encodeSerializableValue(
                            State.Serializer(mappings.state.contentSerializer(type, value.content)),
                            value,
                        )
                    is Message ->
                        encoder.encodeSerializableValue(
                            Message.Serializer(mappings.message.contentSerializer(type, value.content)),
                            value,
                        )
                }
            }
        }
    }

    @Serializable data class Response(@SerialName("delay_id") val delayId: DelayId)

    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Request?,
    ): KSerializer<Request> {
        return Request.Serializer(type, value, mappings)
    }
}
