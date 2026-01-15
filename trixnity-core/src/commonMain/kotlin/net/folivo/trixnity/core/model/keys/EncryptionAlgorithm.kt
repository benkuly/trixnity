package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.serialization.stringWrapperSerializer

@Serializable(with = EncryptionAlgorithm.Serializer::class)
sealed class EncryptionAlgorithm {
    abstract val name: String

    override fun toString(): String {
        return name
    }

    @Serializable(with = Megolm.Serializer::class)
    data object Megolm : EncryptionAlgorithm() {
        override val name: String = "m.megolm.v1.aes-sha2"

        object Serializer : KSerializer<Megolm> by stringWrapperSerializer(Megolm, name)
    }

    @Serializable(with = Olm.Serializer::class)
    data object Olm : EncryptionAlgorithm() {
        override val name: String = "m.olm.v1.curve25519-aes-sha2"

        object Serializer : KSerializer<Olm> by stringWrapperSerializer(Olm, name)
    }

    data class Unknown(override val name: String) : EncryptionAlgorithm()

    object Serializer : KSerializer<EncryptionAlgorithm> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("EncryptionAlgorithm", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): EncryptionAlgorithm {
            return when (val name = decoder.decodeString()) {
                Megolm.name -> Megolm
                Olm.name -> Olm
                else -> Unknown(name)
            }
        }

        override fun serialize(encoder: Encoder, value: EncryptionAlgorithm) {
            encoder.encodeString(value.name)
        }
    }
}