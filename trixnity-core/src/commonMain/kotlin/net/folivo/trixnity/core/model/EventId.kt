package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@Serializable(with = EventIdSerializer::class)
@JvmInline
value class EventId(val full: String) {
    override fun toString() = full
}

object EventIdSerializer : KSerializer<EventId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): EventId = EventId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: EventId) {
        encoder.encodeString(value.full)
    }
}