package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun createDataUnitSerializersModule(
    mappings: EventContentSerializerMappings,
    roomVersionStore: RoomVersionStore,
): SerializersModule {
    val ephemeralDataUnitSerializer = EphemeralDataUnitSerializer(mappings.ephemeralDataUnit)
    val persistentMessageDataUnitSerializer =
        PersistentMessageDataUnitSerializer(mappings.message, roomVersionStore)
    val persistentStateDataUnitSerializer =
        PersistentStateDataUnitSerializer(mappings.state, roomVersionStore)
    val persistentDataUnitSerializer =
        PersistentDataUnitSerializer(persistentMessageDataUnitSerializer, persistentStateDataUnitSerializer)
    val eventTypeSerializer = EventTypeSerializer(mappings)
    return SerializersModule {
        contextual(ephemeralDataUnitSerializer)
        contextual(persistentMessageDataUnitSerializer)
        contextual(persistentStateDataUnitSerializer)
        contextual(persistentDataUnitSerializer)
        contextual(eventTypeSerializer)
    }
}