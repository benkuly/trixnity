package net.folivo.trixnity.crypto.jose

import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64

@Serializable
data class TokenHeader(
    @SerialName("alg") val algorithm: TokenAlgorithm,
    @SerialName("typ") val type: TokenType
)

object JsonWebTokenSerializer : KSerializer<JsonWebToken> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JsonWebToken", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): JsonWebToken = JsonWebToken.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: JsonWebToken) = encoder.encodeString(value.toString())
}

/**
 * @param header    The header containing type and algorithm
 * @param payload   The payload containing the claims of the JWT
 * @param signature The signature of the JWT payload
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7515">RFC7515: JSON Web Signature (JWS)</a>
 */
@Serializable(with = JsonWebTokenSerializer::class)
data class JsonWebToken(val header: TokenHeader, val payload: JsonObject, val signature: ByteArray?) {

    suspend fun verifySignature(publicKey: String): Boolean =
        header.algorithm.verify(publicKey, "${headerString()}.${payloadString()}".encodeToByteArray(), signature)

    private fun headerString(): String = base64.encode(json.encodeToString(header).encodeToByteArray())
    private fun payloadString(): String = base64.encode(json.encodeToString(payload).encodeToByteArray())
    private fun signatureString(): String? = signature?.let { base64.encode(signature) }

    // TODO: Verification steps: Verify issuer and signature

    override fun toString(): String =
        "${headerString()}.${payloadString()}${signatureString()?.let { ".$it" } ?: ""}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as JsonWebToken

        if (header != other.header) return false
        if (payload != other.payload) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object {
        val base64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val json: Json = Json { ignoreUnknownKeys = true }

        fun fromString(value: String): JsonWebToken {
            val sections = value.removeSuffix(".").split(".")
            require(sections.size in 2..3) { "JWT must have 2 or 3 sections" }

            // Deserialize header containing algorithm and token type
            val json = Json { ignoreUnknownKeys = true }
            val header = json.decodeFromString<TokenHeader>(base64.decode(sections[0]).decodeToString())
            require(header.type == TokenType.Jwt) { "JOSE token type is expected to be JWT" }

            val signed = header.algorithm != TokenAlgorithm.None && header.algorithm !is TokenAlgorithm.Other
            require(sections.size == if (signed) 3 else 2) { "JWT section count mismatch for algorithm" }

            // Deserialize body containing raw information TODO: Add claim object with custom claims
            val body: JsonObject = json.decodeFromString(base64.decode(sections[1]).decodeToString())

            // Deserialize signature if present when algorithm is not none
            return JsonWebToken(header, body, if (signed) base64.decode(sections[2]) else null)
        }
    }
}

inline fun <reified T> JsonWebToken.deserializePayload(serializer: KSerializer<T> = serializer<T>()): Claims<T> =
    Claims(
        additional = JsonWebToken.json.decodeFromJsonElement(serializer, payload),
        issuer = payload["iss"]?.jsonPrimitive?.content?.let { Url(it) },
        subject = requireNotNull(payload["sub"]).jsonPrimitive.content,
        issuedAt = payload["iat"]?.jsonPrimitive?.long
    )
