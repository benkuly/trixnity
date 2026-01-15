package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CrossSigningKeysUsage.Serializer::class)
sealed interface CrossSigningKeysUsage {
    val name: String

    data object MasterKey : CrossSigningKeysUsage {
        override val name = "master"
    }

    data object SelfSigningKey : CrossSigningKeysUsage {
        override val name = "self_signing"
    }

    data object UserSigningKey : CrossSigningKeysUsage {
        override val name = "user_signing"
    }

    data class UnknownCrossSigningKeyUsage(
        override val name: String
    ) : CrossSigningKeysUsage

    object Serializer : KSerializer<CrossSigningKeysUsage> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CrossSigningKeysUsage", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): CrossSigningKeysUsage {
            return when (val name = decoder.decodeString()) {
                MasterKey.name -> MasterKey
                SelfSigningKey.name -> SelfSigningKey
                UserSigningKey.name -> UserSigningKey
                else -> UnknownCrossSigningKeyUsage(name)
            }
        }

        override fun serialize(encoder: Encoder, value: CrossSigningKeysUsage) {
            encoder.encodeString(value.name)
        }
    }
}