package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SecretStorageAlgorithmSerializer::class)
sealed class SecretStorageAlgorithm {
    abstract val value: String

    object AesHmacSha2 : SecretStorageAlgorithm() {
        override val value = "m.secret_storage.v1.aes-hmac-sha2"
    }

    data class Unknown(override val value: String) : SecretStorageAlgorithm()
}

object SecretStorageAlgorithmSerializer : KSerializer<SecretStorageAlgorithm> {
    override val descriptor =
        PrimitiveSerialDescriptor("SecretStorageAlgorithmSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SecretStorageAlgorithm {
        return when (val value = decoder.decodeString()) {
            SecretStorageAlgorithm.AesHmacSha2.value -> SecretStorageAlgorithm.AesHmacSha2
            else -> SecretStorageAlgorithm.Unknown(value)
        }
    }

    override fun serialize(encoder: Encoder, value: SecretStorageAlgorithm) {
        encoder.encodeString(value.value)
    }
}