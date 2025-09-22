package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ScopeListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScopeList", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: List<String>) = encoder.encodeString(value.joinToString(" "))
    override fun deserialize(decoder: Decoder): List<String> =
        decoder.decodeString().split(" ").filter { it.isNotBlank() }
}

@Serializable
data class OAuth2TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @Serializable(with = ScopeListSerializer::class) val scope: List<String>? = null,
)
