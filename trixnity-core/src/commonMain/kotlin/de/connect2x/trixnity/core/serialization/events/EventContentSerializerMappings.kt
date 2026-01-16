package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.*

interface EventContentSerializerMappings {
    val message: Set<MessageEventContentSerializerMapping>
    val state: Set<StateEventContentSerializerMapping>
    val ephemeral: Set<EventContentSerializerMapping<EphemeralEventContent>>
    val ephemeralDataUnit: Set<EventContentSerializerMapping<EphemeralDataUnitContent>>
    val toDevice: Set<EventContentSerializerMapping<ToDeviceEventContent>>
    val globalAccountData: Set<EventContentSerializerMapping<GlobalAccountDataEventContent>>
    val roomAccountData: Set<EventContentSerializerMapping<RoomAccountDataEventContent>>
    val block: Set<EventContentBlockSerializerMapping<*>>

    operator fun plus(plus: EventContentSerializerMappings): EventContentSerializerMappings {
        val roomEventContentSerializerMappings = this.message + plus.message
        val stateEventContentSerializerMappings = this.state + plus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral + plus.ephemeral
        val ephemeralDataUnitContentSerializerMappings = this.ephemeralDataUnit + plus.ephemeralDataUnit
        val toDeviceEventContentSerializerMappings = this.toDevice + plus.toDevice
        val globalAccountDataEventContentSerializerMappings = this.globalAccountData + plus.globalAccountData
        val roomAccountDataEventContentSerializerMappings = this.roomAccountData + plus.roomAccountData
        val blockMappings = this.block + plus.block
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val ephemeralDataUnit = ephemeralDataUnitContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val globalAccountData = globalAccountDataEventContentSerializerMappings
            override val roomAccountData = roomAccountDataEventContentSerializerMappings
            override val block = blockMappings
        }
    }

    operator fun minus(minus: EventContentSerializerMappings): EventContentSerializerMappings {
        val roomEventContentSerializerMappings = this.message - minus.message
        val stateEventContentSerializerMappings = this.state - minus.state
        val ephemeralEventContentSerializerMappings = this.ephemeral - minus.ephemeral
        val ephemeralDataUnitContentSerializerMappings = this.ephemeralDataUnit - minus.ephemeralDataUnit
        val toDeviceEventContentSerializerMappings = this.toDevice - minus.toDevice
        val globalAccountDataEventContentSerializerMappings = this.globalAccountData - minus.globalAccountData
        val roomAccountDataEventContentSerializerMappings = this.roomAccountData - minus.roomAccountData
        val blockMappings = this.block - minus.block
        return object : EventContentSerializerMappings {
            override val message = roomEventContentSerializerMappings
            override val state = stateEventContentSerializerMappings
            override val ephemeral = ephemeralEventContentSerializerMappings
            override val ephemeralDataUnit = ephemeralDataUnitContentSerializerMappings
            override val toDevice = toDeviceEventContentSerializerMappings
            override val globalAccountData = globalAccountDataEventContentSerializerMappings
            override val roomAccountData = roomAccountDataEventContentSerializerMappings
            override val block = blockMappings
        }
    }

    companion object
}