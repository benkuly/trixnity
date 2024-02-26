package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = EventIdSerializer::class)
data class EventId(val opaque: String, val room: Mention): Mention {
    constructor(opaque: String) : this(opaque.removePrefix("$"), RoomId(""))

    companion object {
        const val sigilCharacter = '$'
    }

    override val localpart: String
        get() = room.localpart
    override val domain: String
        get() = room.domain
    override val full: String
        get() = "$sigilCharacter$opaque"

    override fun toString() = "$sigilCharacter$opaque"
}

object EventIdSerializer : KSerializer<EventId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): EventId = EventId(decoder.decodeString(), RoomId("",""))

    override fun serialize(encoder: Encoder, value: EventId) {
        encoder.encodeString(value.full)
    }
}