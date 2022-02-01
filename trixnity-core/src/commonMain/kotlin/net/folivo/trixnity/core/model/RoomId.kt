package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoomIdSerializer::class)
class RoomId : MatrixId {
    constructor(full: String) : super(full, '!')
    constructor(localpart: String, domain: String) : super(localpart, domain, '!')
}

object RoomIdSerializer : KSerializer<RoomId> {
    override fun deserialize(decoder: Decoder): RoomId {
        return RoomId(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: RoomId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomIdSerializer", PrimitiveKind.STRING)
}