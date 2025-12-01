package net.folivo.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @Serializable(with = ScopeListSerializer::class) @SerialName("scope") val scope: Set<String>? = null,
)

object ScopeListSerializer : KSerializer<Set<String>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ScopeList", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Set<String>) = encoder.encodeString(value.joinToString(" "))
    override fun deserialize(decoder: Decoder): Set<String> =
        decoder.decodeString().split(" ").filter { it.isNotBlank() }.toSet()
}