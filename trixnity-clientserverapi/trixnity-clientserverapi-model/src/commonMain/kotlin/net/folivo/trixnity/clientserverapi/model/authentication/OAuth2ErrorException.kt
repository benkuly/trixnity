package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.clientserverapi.model.authentication.OAuth2ErrorException.OAuth2ErrorType

object OAuth2ErrorTypeSerializer : KSerializer<OAuth2ErrorType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("OAuth2ErrorType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OAuth2ErrorType) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): OAuth2ErrorType = when (val value = decoder.decodeString()) {
        OAuth2ErrorType.InvalidRequest.value -> OAuth2ErrorType.InvalidRequest
        OAuth2ErrorType.UnauthorizedClient.value -> OAuth2ErrorType.UnauthorizedClient
        OAuth2ErrorType.AccessDenied.value -> OAuth2ErrorType.AccessDenied
        OAuth2ErrorType.UnsupportedResponseType.value -> OAuth2ErrorType.UnsupportedResponseType
        OAuth2ErrorType.InvalidScope.value -> OAuth2ErrorType.InvalidScope
        OAuth2ErrorType.ServerError.value -> OAuth2ErrorType.ServerError
        OAuth2ErrorType.TemporarilyUnavailable.value -> OAuth2ErrorType.TemporarilyUnavailable
        OAuth2ErrorType.InvalidGrant.value -> OAuth2ErrorType.InvalidGrant
        else -> OAuth2ErrorType.Unknown(value)
    }
}


@Serializable
data class OAuth2ErrorException(
    val error: OAuth2ErrorType,
    val description: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    val state: String? = null
) : RuntimeException(description) {
    @Serializable(with = OAuth2ErrorTypeSerializer::class)
    sealed interface OAuth2ErrorType {
        val value: String

        @Serializable
        data object InvalidRequest : OAuth2ErrorType {
            override val value: String = "invalid_request"
        }

        @Serializable
        data object UnauthorizedClient : OAuth2ErrorType {
            override val value: String = "unauthorized_client"
        }

        @Serializable
        data object AccessDenied : OAuth2ErrorType {
            override val value: String = "access_denied"
        }

        @Serializable
        data object UnsupportedResponseType : OAuth2ErrorType {
            override val value: String = "unsupported_response_type"
        }

        @Serializable
        data object InvalidScope : OAuth2ErrorType {
            override val value: String = "invalid_scope"
        }

        @Serializable
        data object InvalidGrant : OAuth2ErrorType {
            override val value: String = "invalid_grant"
        }

        @Serializable
        data object ServerError : OAuth2ErrorType {
            override val value: String = "server_error"
        }

        @Serializable
        data object TemporarilyUnavailable : OAuth2ErrorType {
            override val value: String = "temporarily_unavailable"
        }

        @Serializable
        data class Unknown(override val value: String) : OAuth2ErrorType
    }
}
