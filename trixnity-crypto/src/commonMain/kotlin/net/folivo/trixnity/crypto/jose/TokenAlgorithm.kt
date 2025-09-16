package net.folivo.trixnity.crypto.jose

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.crypto.core.signSha256WithRSA
import net.folivo.trixnity.crypto.core.verifySha256WithRSA

object TokenAlgorithmSerializer : KSerializer<TokenAlgorithm> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("JwtType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TokenAlgorithm) = encoder.encodeString(value.internal)
    override fun deserialize(decoder: Decoder): TokenAlgorithm = when(val internal = decoder.decodeString()) {
        "RS256" -> TokenAlgorithm.RS256
        "none" -> TokenAlgorithm.None
        else -> TokenAlgorithm.Other(internal)
    }
}

@Serializable(with = TokenAlgorithmSerializer::class)
sealed class TokenAlgorithm(internal val internal: String) {
    abstract suspend fun sign(key: ByteArray, payload: ByteArray): ByteArray?
    abstract suspend fun verify(key: ByteArray, payload: ByteArray, signature: ByteArray?): Boolean

    object RS256 : TokenAlgorithm("RS256") {
        override suspend fun sign(key: ByteArray, payload: ByteArray): ByteArray = signSha256WithRSA(key, payload)
        override suspend fun verify(key: ByteArray, payload: ByteArray, signature: ByteArray?): Boolean =
            verifySha256WithRSA(key, payload, checkNotNull(signature) { "Signature is not expected to be null" })
    }

    object None : TokenAlgorithm("None") {
        override suspend fun sign(key: ByteArray, payload: ByteArray): ByteArray? = null
        override suspend fun verify(key: ByteArray, payload: ByteArray, signature: ByteArray?): Boolean = true
    }

    class Other(internal: String) : TokenAlgorithm(internal) {
        override suspend fun sign(key: ByteArray, payload: ByteArray): ByteArray? = null
        override suspend fun verify(key: ByteArray, payload: ByteArray, signature: ByteArray?): Boolean = false
    }
}
