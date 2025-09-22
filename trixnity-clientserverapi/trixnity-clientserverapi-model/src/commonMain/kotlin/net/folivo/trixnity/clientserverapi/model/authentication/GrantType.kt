package net.folivo.trixnity.clientserverapi.model.authentication

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
        GrantType.AuthorizationCode.value -> GrantType.AuthorizationCode
        GrantType.RefreshToken.value -> GrantType.RefreshToken
        else -> GrantType.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: GrantType) = encoder.encodeString(value.value)
}


@Serializable(with = GrantTypeSerializer::class)
sealed interface GrantType {
    val value: String

    object AuthorizationCode : GrantType {
        override val value: String = "authorization_code"
    }

    object RefreshToken : GrantType {
        override val value: String = "refresh_token"
    }

    data class Unknown(override val value: String) : GrantType
}
