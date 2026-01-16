package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SecretStorageAlgorithm.Serializer::class)
sealed interface SecretStorageAlgorithm {
    val value: String

    data object AesHmacSha2 : SecretStorageAlgorithm {
        override val value = "m.secret_storage.v1.aes-hmac-sha2"
    }

    data class Unknown(override val value: String) : SecretStorageAlgorithm

    object Serializer : KSerializer<SecretStorageAlgorithm> {
        override val descriptor =
            PrimitiveSerialDescriptor("SecretStorageAlgorithm", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): SecretStorageAlgorithm {
            return when (val value = decoder.decodeString()) {
                AesHmacSha2.value -> AesHmacSha2
                else -> Unknown(value)
            }
        }

        override fun serialize(encoder: Encoder, value: SecretStorageAlgorithm) {
            encoder.encodeString(value.value)
        }
    }
}