package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = GrantType.Serializer::class)
sealed interface GrantType {
    val value: String

    object AuthorizationCode : GrantType {
        override val value: String = "authorization_code"
    }

    object RefreshToken : GrantType {
        override val value: String = "refresh_token"
    }

    data class Unknown(override val value: String) : GrantType

    object Serializer : KSerializer<GrantType> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("GrantType", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): GrantType = when (val value = decoder.decodeString().lowercase()) {
            AuthorizationCode.value -> AuthorizationCode
            RefreshToken.value -> RefreshToken
            else -> Unknown(value)
        }

        override fun serialize(encoder: Encoder, value: GrantType) = encoder.encodeString(value.value)
    }
}