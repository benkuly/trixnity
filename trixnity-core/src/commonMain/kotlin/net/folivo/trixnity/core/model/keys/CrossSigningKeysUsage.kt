package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CrossSigningKeyUsageSerializer::class)
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
}

object CrossSigningKeyUsageSerializer : KSerializer<CrossSigningKeysUsage> {
    override fun deserialize(decoder: Decoder): CrossSigningKeysUsage {
        return when (val name = decoder.decodeString()) {
            CrossSigningKeysUsage.MasterKey.name -> CrossSigningKeysUsage.MasterKey
            CrossSigningKeysUsage.SelfSigningKey.name -> CrossSigningKeysUsage.SelfSigningKey
            CrossSigningKeysUsage.UserSigningKey.name -> CrossSigningKeysUsage.UserSigningKey
            else -> CrossSigningKeysUsage.UnknownCrossSigningKeyUsage(name)
        }
    }

    override fun serialize(encoder: Encoder, value: CrossSigningKeysUsage) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CrossSigningKeyUsageSerializer", PrimitiveKind.STRING)
}