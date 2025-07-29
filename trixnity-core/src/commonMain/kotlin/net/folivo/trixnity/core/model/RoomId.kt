package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.util.MatrixIdRegex

@Serializable(with = RoomIdSerializer::class)
data class RoomId(val full: String) {

    companion object {
        const val sigilCharacter = '!'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.roomIdRegex)
    }

    val isValid by lazy { isValid(full) }

    override fun toString() = full
}

object RoomIdSerializer : KSerializer<RoomId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RoomId = RoomId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: RoomId) {
        encoder.encodeString(value.full)
    }
}