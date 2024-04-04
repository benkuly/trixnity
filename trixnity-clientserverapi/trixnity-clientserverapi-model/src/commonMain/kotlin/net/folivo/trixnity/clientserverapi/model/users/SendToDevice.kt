package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.UserIdSerializer
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/sendToDevice/{type}/{txnId}")
@HttpMethod(PUT)
data class SendToDevice(
    @SerialName("type") val type: String,
    @SerialName("txnId") val txnId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SendToDevice.Request, Unit> {
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Request?
    ): KSerializer<Request> {
        val baseSerializer =
            mappings.toDevice.contentSerializer(type, value?.messages?.values?.firstOrNull()?.values?.firstOrNull())
        return SendToDeviceRequestSerializer(baseSerializer)
    }

    data class Request(
        val messages: Map<UserId, Map<String, ToDeviceEventContent>>
    )
}

class SendToDeviceRequestSerializer(baseSerializer: KSerializer<ToDeviceEventContent>) :
    KSerializer<SendToDevice.Request> {
    override val descriptor = buildClassSerialDescriptor("SendToDeviceRequestSerializer")

    private val messagesSerializer = MapSerializer(
        UserIdSerializer,
        MapSerializer(String.serializer(), baseSerializer)
    )

    override fun deserialize(decoder: Decoder): SendToDevice.Request {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("send to device request was no object")
        val messages = jsonObject["messages"]
        if (messages !is JsonObject) throw SerializationException("send to device request messages was no object")
        return SendToDevice.Request(
            decoder.json.decodeFromJsonElement(messagesSerializer, messages)
        )
    }

    override fun serialize(encoder: Encoder, value: SendToDevice.Request) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            JsonObject(
                mapOf(
                    "messages" to encoder.json.encodeToJsonElement(messagesSerializer, value.messages)
                )
            )
        )
    }
}