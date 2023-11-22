package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.*

interface EventContentSerializerMappings {
    val message: Set<MessageEventContentSerializerMapping>
    val state: Set<StateEventContentSerializerMapping>
    val ephemeral: Set<EventContentSerializerMapping<EphemeralEventContent>>
    val ephemeralDataUnit: Set<EventContentSerializerMapping<EphemeralDataUnitContent>>
    val toDevice: Set<EventContentSerializerMapping<ToDeviceEventContent>>
    val globalAccountData: Set<EventContentSerializerMapping<GlobalAccountDataEventContent>>
    val roomAccountData: Set<EventContentSerializerMapping<RoomAccountDataEventContent>>

    operator fun plus(plus: EventContentSerializerMappings?): EventContentSerializerMappings {
        if (plus == null) return this
        val roomEventContentSerializerMappings = this.message + plus.message
        val stateEventContentSerializerMappings = this.state + plus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral + plus.ephemeral
        val ephemeralDataUnitContentSerializerMappings = this.ephemeralDataUnit + plus.ephemeralDataUnit
        val toDeviceEventContentSerializerMappings = this.toDevice + plus.toDevice
        val globalAccountDataEventContentSerializerMappings = this.globalAccountData + plus.globalAccountData
        val roomAccountDataEventContentSerializerMappings = this.roomAccountData + plus.roomAccountData
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val ephemeralDataUnit = ephemeralDataUnitContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val globalAccountData = globalAccountDataEventContentSerializerMappings
            override val roomAccountData = roomAccountDataEventContentSerializerMappings
        }
    }

    operator fun minus(minus: EventContentSerializerMappings?): EventContentSerializerMappings {
        if (minus == null) return this
        val roomEventContentSerializerMappings = this.message - minus.message
        val stateEventContentSerializerMappings = this.state - minus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral - minus.ephemeral
        val ephemeralDataUnitContentSerializerMappings = this.ephemeralDataUnit - minus.ephemeralDataUnit
        val toDeviceEventContentSerializerMappings = this.toDevice - minus.toDevice
        val globalAccountDataEventContentSerializerMappings = this.globalAccountData - minus.globalAccountData
        val roomAccountDataEventContentSerializerMappings = this.roomAccountData - minus.roomAccountData
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val ephemeralDataUnit = ephemeralDataUnitContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val globalAccountData = globalAccountDataEventContentSerializerMappings
            override val roomAccountData = roomAccountDataEventContentSerializerMappings
        }
    }
}