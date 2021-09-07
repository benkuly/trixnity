package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

object KeyAlgorithmSerializer : KSerializer<KeyAlgorithm> {
    override fun deserialize(decoder: Decoder): KeyAlgorithm {
        return KeyAlgorithm.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: KeyAlgorithm) {
        encoder.encodeString(value.name)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KeyAlgorithm", PrimitiveKind.STRING)
}