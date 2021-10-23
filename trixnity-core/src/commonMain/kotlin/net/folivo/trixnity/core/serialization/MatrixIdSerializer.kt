package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.*

object MatrixIdSerializer : KSerializer<MatrixId> {
    override fun deserialize(decoder: Decoder): MatrixId {
        return try {
            MatrixId.of(decoder.decodeString())
        } catch (ex: IllegalArgumentException) {
            throw SerializationException(ex.message)
        }
    }

    override fun serialize(encoder: Encoder, value: MatrixId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MatrixId", PrimitiveKind.STRING)
}

object UserIdSerializer : KSerializer<UserId> {
    override fun deserialize(decoder: Decoder): UserId {
        return try {
            UserId(decoder.decodeString())
        } catch (ex: IllegalArgumentException) {
            throw SerializationException(ex.message)
        }
    }

    override fun serialize(encoder: Encoder, value: UserId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UserId", PrimitiveKind.STRING)
}

object RoomIdSerializer : KSerializer<RoomId> {
    override fun deserialize(decoder: Decoder): RoomId {
        return try {
            RoomId(decoder.decodeString())
        } catch (ex: IllegalArgumentException) {
            throw SerializationException(ex.message)
        }
    }

    override fun serialize(encoder: Encoder, value: RoomId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomId", PrimitiveKind.STRING)
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

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomAliasId", PrimitiveKind.STRING)
}

object EventIdSerializer : KSerializer<EventId> {
    override fun deserialize(decoder: Decoder): EventId {
        return try {
            EventId(decoder.decodeString())
        } catch (ex: IllegalArgumentException) {
            throw SerializationException(ex.message)
        }
    }

    override fun serialize(encoder: Encoder, value: EventId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventId", PrimitiveKind.STRING)
}