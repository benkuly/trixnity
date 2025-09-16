package net.folivo.trixnity.crypto.jose

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TokenTypeSerializer : KSerializer<TokenType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("JwtType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TokenType) = encoder.encodeString(value.internal)
    override fun deserialize(decoder: Decoder): TokenType = when (val internal = decoder.decodeString().uppercase()) {
        "JWT" -> TokenType.Jwt
        else -> TokenType.Other(internal)
    }
}

@Serializable(with = TokenTypeSerializer::class)
sealed class TokenType(internal val internal: String) {
    object Jwt : TokenType("JWT")
    class Other(internal: String) : TokenType(internal)
}
