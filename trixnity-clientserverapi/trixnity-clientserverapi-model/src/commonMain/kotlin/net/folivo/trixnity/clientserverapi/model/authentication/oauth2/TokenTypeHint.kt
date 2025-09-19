package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TokenTypeHintSerializer : KSerializer<TokenTypeHint> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("TokenTypeHint", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TokenTypeHint) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): TokenTypeHint = when (val value = decoder.decodeString().lowercase()) {
        "refresh_token" -> TokenTypeHint.RefreshToken
        "access_token" -> TokenTypeHint.AccessToken
        else -> TokenTypeHint.Other(value)
    }
}

@Serializable(with = TokenTypeHintSerializer::class)
sealed class TokenTypeHint(val value: String) {
    object RefreshToken : TokenTypeHint("refresh_token")
    object AccessToken : TokenTypeHint("access_token")
    class Other(value: String) : TokenTypeHint(value)
}
