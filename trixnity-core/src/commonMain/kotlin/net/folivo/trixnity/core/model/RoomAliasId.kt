package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoomAliasIdSerializer::class)
class RoomAliasId : MatrixId {
    constructor(full: String) : super(full, '#')
    constructor(localpart: String, domain: String) : super(localpart, domain, '#')
}

object RoomAliasIdSerializer : KSerializer<RoomAliasId> {
    override fun deserialize(decoder: Decoder): RoomAliasId {
        return try {
            RoomAliasId(decoder.decodeString())
        } catch (ex: IllegalArgumentException) {
            throw SerializationException(ex.message)
        }
    }

    override fun serialize(encoder: Encoder, value: RoomAliasId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomAliasIdSerializer", PrimitiveKind.STRING)
}