package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class MatrixIdSerializer : KSerializer<net.folivo.trixnity.core.model.MatrixId> {
    override fun deserialize(decoder: Decoder): net.folivo.trixnity.core.model.MatrixId {
        return net.folivo.trixnity.core.model.MatrixId.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: net.folivo.trixnity.core.model.MatrixId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MatrixId", PrimitiveKind.STRING)
}