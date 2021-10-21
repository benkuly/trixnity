package net.folivo.trixnity.core.serialization.event

import net.folivo.trixnity.core.model.events.*

interface EventContentSerializerMappings {
    val message: Set<EventContentSerializerMapping<out MessageEventContent>>
    val state: Set<EventContentSerializerMapping<out StateEventContent>>
    val ephemeral: Set<EventContentSerializerMapping<out EphemeralEventContent>>
    val toDevice: Set<EventContentSerializerMapping<out ToDeviceEventContent>>
    val globalAccountData: Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>>
    val roomAccountData: Set<EventContentSerializerMapping<out RoomAccountDataEventContent>>

    operator fun plus(plus: EventContentSerializerMappings?): EventContentSerializerMappings {
        if (plus == null) return this
        val roomEventContentSerializerMappings = this.message + plus.message
        val stateEventContentSerializerMappings = this.state + plus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral + plus.ephemeral
        val toDeviceEventContentSerializerMappings = this.toDevice + plus.toDevice
        val globalAccountDataEventContentSerializerMappings = this.globalAccountData + plus.globalAccountData
        val roomAccountDataEventContentSerializerMappings = this.roomAccountData + plus.roomAccountData
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val globalAccountData = globalAccountDataEventContentSerializerMappings
            override val roomAccountData = roomAccountDataEventContentSerializerMappings
        }
    }
}
