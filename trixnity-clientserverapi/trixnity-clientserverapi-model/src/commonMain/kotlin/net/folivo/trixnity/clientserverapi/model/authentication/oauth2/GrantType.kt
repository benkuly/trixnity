package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object GrantTypeSerializer : KSerializer<GrantType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("GrantType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): GrantType = when (val value = decoder.decodeString().lowercase()) {
        "authorization_code" -> GrantType.AuthorizationCode
        "refresh_token" -> GrantType.RefreshToken
        else -> GrantType.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: GrantType) = encoder.encodeString(value.toString())
}


@Serializable(with = GrantTypeSerializer::class)
sealed interface GrantType {
    object AuthorizationCode : GrantType {
        override fun toString(): String = "authorization_code"
    }

    object RefreshToken : GrantType {
        override fun toString(): String = "refresh_token"
    }

    data class Unknown(private val value: String) : GrantType {
        override fun toString(): String = value
    }
}
