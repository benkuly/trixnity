package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.*

abstract class BaseEventContentSerializerMappings : EventContentSerializerMappings {
    override val message = setOf<EventContentSerializerMapping<out MessageEventContent>>()
    override val state = setOf<EventContentSerializerMapping<out StateEventContent>>()
    override val ephemeral = setOf<EventContentSerializerMapping<out EphemeralEventContent>>()
    override val toDevice = setOf<EventContentSerializerMapping<out ToDeviceEventContent>>()
    override val globalAccountData = setOf<EventContentSerializerMapping<out GlobalAccountDataEventContent>>()
    override val roomAccountData = setOf<EventContentSerializerMapping<out RoomAccountDataEventContent>>()
}