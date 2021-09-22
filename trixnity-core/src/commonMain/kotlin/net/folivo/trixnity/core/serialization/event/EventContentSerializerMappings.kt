package net.folivo.trixnity.core.serialization.event

import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

interface EventContentSerializerMappings {
    val room: Set<EventContentSerializerMapping<out MessageEventContent>>
    val state: Set<EventContentSerializerMapping<out StateEventContent>>
    val ephemeral: Set<EventContentSerializerMapping<out EphemeralEventContent>>
    val toDevice: Set<EventContentSerializerMapping<out ToDeviceEventContent>>

    operator fun plus(plus: EventContentSerializerMappings?): EventContentSerializerMappings {
        val roomEventContentSerializerMappings = this.room + (plus?.room ?: setOf())
        val stateEventContentSerializerMappings = this.state + (plus?.state ?: setOf())
        val ephemeralEventContentSerializerMappings = this.ephemeral + (plus?.ephemeral ?: setOf())
        val toDeviceEventContentSerializerMappings = this.toDevice + (plus?.toDevice ?: setOf())
        return object : EventContentSerializerMappings {
            override val room = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
        }
    }
}
