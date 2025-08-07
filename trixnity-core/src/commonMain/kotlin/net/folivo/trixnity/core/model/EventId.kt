package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.util.MatrixIdRegex

@Serializable(with = EventIdSerializer::class)
data class EventId(val full: String) {
    companion object {
        const val sigilCharacter = '$'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.eventId)
        fun isReasonable(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.reasonableEventId)
    }

    val isValid by lazy { isValid(full) }
    val isReasonable by lazy { isReasonable(full) }

    override fun toString() = full
}

object EventIdSerializer : KSerializer<EventId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): EventId = EventId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: EventId) {
        encoder.encodeString(value.full)
    }
}