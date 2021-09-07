package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm

object EncryptionAlgorithmSerializer : KSerializer<EncryptionAlgorithm> {
    override fun deserialize(decoder: Decoder): EncryptionAlgorithm {
        return EncryptionAlgorithm.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: EncryptionAlgorithm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EncryptionAlgorithm", PrimitiveKind.STRING)
}