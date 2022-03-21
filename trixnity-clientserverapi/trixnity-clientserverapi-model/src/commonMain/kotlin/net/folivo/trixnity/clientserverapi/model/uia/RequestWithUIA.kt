package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = RequestWithUIASerializer::class)
data class RequestWithUIA<T>(
    val request: T,
    val authentication: AuthenticationRequestWithSession?,
)

class RequestWithUIASerializer<T>(private val baseSerializer: KSerializer<T>) :
    KSerializer<RequestWithUIA<T>> {
    override val descriptor = buildClassSerialDescriptor("RequestWithUIASerializer")

    override fun deserialize(decoder: Decoder): RequestWithUIA<T> {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("expected request to be json object")
        val request = decoder.json.decodeFromJsonElement(baseSerializer, jsonObject)
        val authObject = jsonObject["auth"]
            ?.let { if (it !is JsonObject) throw SerializationException("expected auth to be json object") else it }
        val auth = authObject?.let { decoder.json.decodeFromJsonElement<AuthenticationRequestWithSession>(it) }
        return RequestWithUIA(request, auth)
    }

    override fun serialize(encoder: Encoder, value: RequestWithUIA<T>) {
        require(encoder is JsonEncoder)
        val jsonObject = JsonObject(
            buildMap {
                putAll(encoder.json.encodeToJsonElement(baseSerializer, value.request).jsonObject)
                value.authentication?.let { authentication ->
                    put("auth", encoder.json.encodeToJsonElement(authentication))
                }
            }
        )
        encoder.encodeJsonElement(jsonObject)
    }

}