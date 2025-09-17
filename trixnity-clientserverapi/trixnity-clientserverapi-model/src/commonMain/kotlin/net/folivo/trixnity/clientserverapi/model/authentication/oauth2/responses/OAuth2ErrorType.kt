package net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object OAuth2ErrorTypeSerializer : KSerializer<OAuth2ErrorType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("OAuth2ErrorType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OAuth2ErrorType) = encoder.encodeString(value.internal)
    override fun deserialize(decoder: Decoder): OAuth2ErrorType = when (val value = decoder.decodeString()) {
        "invalid_request" -> OAuth2ErrorType.InvalidRequest
        "unauthorized_client" -> OAuth2ErrorType.UnauthorizedClient
        "access_denied" -> OAuth2ErrorType.AccessDenied
        "unsupported_response_type" -> OAuth2ErrorType.UnsupportedResponseType
        "invalid_scope" -> OAuth2ErrorType.InvalidScope
        "server_error" -> OAuth2ErrorType.ServerError
        "temporarily_unavailable" -> OAuth2ErrorType.TemporarilyUnavailable
        else -> OAuth2ErrorType.Other(value)
    }
}

@Serializable(with = OAuth2ErrorTypeSerializer::class)
sealed class OAuth2ErrorType(internal val internal: String) {
    object InvalidRequest : OAuth2ErrorType("invalid_request")
    object UnauthorizedClient : OAuth2ErrorType("unauthorized_client")
    object AccessDenied : OAuth2ErrorType("access_denied")
    object UnsupportedResponseType : OAuth2ErrorType("unsupported_response_type")
    object InvalidScope : OAuth2ErrorType("invalid_scope")
    object ServerError : OAuth2ErrorType("server_error")
    object TemporarilyUnavailable : OAuth2ErrorType("temporarily_unavailable")
    class Other(internal: String) : OAuth2ErrorType(internal) {
        override fun toString(): String = "Other($internal)"
    }
}
