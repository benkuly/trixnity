package net.folivo.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = TokenEndpointAuthMethod.Serializer::class)
internal sealed interface TokenEndpointAuthMethod {
    val value: String

    object None : TokenEndpointAuthMethod {
        override val value: String = "none"
    }

    data class Unknown(override val value: String) : TokenEndpointAuthMethod

    object Serializer : KSerializer<TokenEndpointAuthMethod> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("TokenEndpointAuthMethod", SerialKind.ENUM)

        override fun deserialize(decoder: Decoder): TokenEndpointAuthMethod =
            when (val value = decoder.decodeString().lowercase()) {
                None.value -> None
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: TokenEndpointAuthMethod) = encoder.encodeString(value.value)
    }
}