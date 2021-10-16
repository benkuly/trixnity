package net.folivo.trixnity.core.serialization.event

import net.folivo.trixnity.core.model.events.*

interface EventContentSerializerMappings {
    val message: Set<EventContentSerializerMapping<out MessageEventContent>>
    val state: Set<EventContentSerializerMapping<out StateEventContent>>
    val ephemeral: Set<EventContentSerializerMapping<out EphemeralEventContent>>
    val toDevice: Set<EventContentSerializerMapping<out ToDeviceEventContent>>
    val accountData: Set<EventContentSerializerMapping<out AccountDataEventContent>>

    operator fun plus(plus: EventContentSerializerMappings?): EventContentSerializerMappings {
        if (plus == null) return this
        val roomEventContentSerializerMappings = this.message + plus.message
        val stateEventContentSerializerMappings = this.state + plus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral + plus.ephemeral
        val toDeviceEventContentSerializerMappings = this.toDevice + plus.toDevice
        val accountDataEventContentSerializerMappings = this.accountData + plus.accountData
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val accountData = accountDataEventContentSerializerMappings
        }
    }
}
