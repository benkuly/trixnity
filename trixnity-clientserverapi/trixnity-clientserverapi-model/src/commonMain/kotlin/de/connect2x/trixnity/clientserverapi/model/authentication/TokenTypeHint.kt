package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TokenTypeHint.Serializer::class)
sealed interface TokenTypeHint {
    val value: String

    object RefreshToken : TokenTypeHint {
        override val value: String = "refresh_token"
    }

    object AccessToken : TokenTypeHint {
        override val value: String = "access_token"
    }

    data class Unknown(override val value: String) : TokenTypeHint

    object Serializer : KSerializer<TokenTypeHint> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("TokenTypeHint", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: TokenTypeHint) = encoder.encodeString(value.value)
        override fun deserialize(decoder: Decoder): TokenTypeHint =
            when (val value = decoder.decodeString().lowercase()) {
                RefreshToken.value -> RefreshToken
                AccessToken.value -> AccessToken
                else -> Unknown(value)
            }
    }
}
