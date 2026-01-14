package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer
import kotlin.jvm.JvmInline

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/state/{type}/{stateKey?}")
@HttpMethod(GET)
data class GetStateEvent(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("type") val type: String,
    @SerialName("stateKey") val stateKey: String = "",
    @SerialName("format") val format: Format? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetStateEvent.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Response?
    ): KSerializer<Response> =
        Response.Serializer(
            format ?: Format.CONTENT,
            checkNotNull(json.serializersModule.getContextual(ClientEvent.StateBaseEvent::class)),
            mappings.state.contentSerializer(type, (value as? Response.Content)?.value),
        )

    @Serializable
    enum class Format {
        @SerialName("event")
        EVENT,

        @SerialName("content")
        CONTENT
    }

    interface Response {
        @JvmInline
        value class Event(val value: @Contextual ClientEvent.StateBaseEvent<*>) : Response

        @JvmInline
        value class Content(val value: StateEventContent) : Response

        class Serializer(
            private val format: Format,
            private val stateBaseEventSerializer: KSerializer<ClientEvent.StateBaseEvent<*>>,
            private val stateEventContentSerializer: KSerializer<StateEventContent>,
        ) : KSerializer<Response> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GetStateEvent.Response")

            @OptIn(ExperimentalSerializationApi::class)
            override fun deserialize(decoder: Decoder): Response {
                return when (format) {
                    Format.EVENT -> {
                        Event(decoder.decodeSerializableValue(stateBaseEventSerializer))
                    }

                    Format.CONTENT -> {
                        Content(decoder.decodeSerializableValue(stateEventContentSerializer))
                    }
                }
            }

            override fun serialize(encoder: Encoder, value: Response) {
                when (value) {
                    is Event -> encoder.encodeSerializableValue(stateBaseEventSerializer, value.value)
                    is Content -> encoder.encodeSerializableValue(stateEventContentSerializer, value.value)
                }
            }
        }
    }
}