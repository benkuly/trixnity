package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.*

abstract class BaseEventContentSerializerMappings : EventContentSerializerMappings {
    override val message = setOf<SerializerMapping<out MessageEventContent>>()
    override val state = setOf<SerializerMapping<out StateEventContent>>()
    override val ephemeral = setOf<SerializerMapping<out EphemeralEventContent>>()
    override val ephemeralDataUnit = setOf<SerializerMapping<out EphemeralDataUnitContent>>()
    override val toDevice = setOf<SerializerMapping<out ToDeviceEventContent>>()
    override val globalAccountData = setOf<SerializerMapping<out GlobalAccountDataEventContent>>()
    override val roomAccountData = setOf<SerializerMapping<out RoomAccountDataEventContent>>()
}