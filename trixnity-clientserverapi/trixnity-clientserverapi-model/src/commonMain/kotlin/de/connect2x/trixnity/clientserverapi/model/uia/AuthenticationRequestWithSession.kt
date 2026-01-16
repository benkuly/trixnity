package de.connect2x.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = AuthenticationRequestWithSession.Serializer::class)
data class AuthenticationRequestWithSession(
    val authenticationRequest: AuthenticationRequest,
    val session: String?
) {
    object Serializer : KSerializer<AuthenticationRequestWithSession> {
        override val descriptor = buildClassSerialDescriptor("AuthenticationRequestWithSession")

        override fun deserialize(decoder: Decoder): AuthenticationRequestWithSession {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement()
            if (jsonObject !is JsonObject) throw SerializationException("should be json object")
            val authenticationRequest = decoder.json.decodeFromJsonElement<AuthenticationRequest>(jsonObject)
            val session =
                jsonObject["session"]?.let {
                    it as? JsonPrimitive ?: throw SerializationException("sesssion must be a string")
                }?.content
            return AuthenticationRequestWithSession(authenticationRequest, session)
        }

        override fun serialize(encoder: Encoder, value: AuthenticationRequestWithSession) {
            require(encoder is JsonEncoder)
            val session = value.session
            encoder.encodeJsonElement(JsonObject(buildMap {
                putAll(encoder.json.encodeToJsonElement(value.authenticationRequest).jsonObject)
                session?.let { put("session", JsonPrimitive(it)) }
            }))
        }
    }
}