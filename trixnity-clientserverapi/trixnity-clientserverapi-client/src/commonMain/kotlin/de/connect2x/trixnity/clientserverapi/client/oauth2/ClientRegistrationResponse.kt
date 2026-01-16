package de.connect2x.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = ClientRegistrationResponse.Serializer::class)
internal data class ClientRegistrationResponse(
    val clientId: String,
    val clientMetadata: ClientMetadata,
) {
    internal object Serializer : KSerializer<ClientRegistrationResponse> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClientRegistrationResponse")

        override fun deserialize(decoder: Decoder): ClientRegistrationResponse {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement()
            require(jsonObject is JsonObject)
            val clientId = requireNotNull(jsonObject["client_id"] as? JsonPrimitive).content
            return ClientRegistrationResponse(
                clientId = clientId,
                clientMetadata = decoder.json.decodeFromJsonElement(jsonObject)
            )
        }

        override fun serialize(encoder: Encoder, value: ClientRegistrationResponse) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(JsonObject(buildMap {
                put("client_id", JsonPrimitive(value.clientId))
                putAll(encoder.json.encodeToJsonElement(value.clientMetadata).jsonObject)
            }))
        }
    }
}