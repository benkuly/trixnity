package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private class TokenEndpointAuthMethodSerializer : KSerializer<TokenEndpointAuthMethod> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("TokenEndpointAuthMethod", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): TokenEndpointAuthMethod = when(val value = decoder.decodeString().lowercase()) {
        "none" -> TokenEndpointAuthMethod.None
        else -> TokenEndpointAuthMethod.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: TokenEndpointAuthMethod) = encoder.encodeString(value.toString())
}


@Serializable(with = TokenEndpointAuthMethodSerializer::class)
sealed interface TokenEndpointAuthMethod {
    object None : TokenEndpointAuthMethod {
        override fun toString(): String = "none"
    }

    data class Unknown(private val value: String) : TokenEndpointAuthMethod {
        override fun toString(): String = value
    }
}
