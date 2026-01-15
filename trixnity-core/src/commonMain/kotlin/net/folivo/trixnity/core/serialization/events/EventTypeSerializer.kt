package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.EventType

class EventTypeSerializer(
    val mappings: EventContentSerializerMappings,
) : KSerializer<EventType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EventType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): EventType {
        val type = decoder.decodeString()
        val mapping = mappings.message.find { it.type == type }
            ?: mappings.state.find { it.type == type }
            ?: mappings.ephemeral.find { it.type == type }
            ?: mappings.toDevice.find { it.type == type }
            ?: mappings.globalAccountData.find { it.type == type }
            ?: mappings.roomAccountData.find { it.type == type }
        return EventType(mapping?.kClass, type)
    }

    override fun serialize(encoder: Encoder, value: EventType) {
        encoder.encodeString(value.name)
    }
}