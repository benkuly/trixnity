package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.MatrixId

object MatrixIdSerializer : KSerializer<MatrixId> {
    override fun deserialize(decoder: Decoder): MatrixId {
        return MatrixId.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: MatrixId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MatrixId", PrimitiveKind.STRING)
}