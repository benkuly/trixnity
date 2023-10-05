package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.RoomId

typealias GetRoomVersionFunction = (RoomId) -> String

fun createDataUnitSerializersModule(
    mappings: EventContentSerializerMappings,
    getRoomVersion: GetRoomVersionFunction,
): SerializersModule {
    val ephemeralDataUnitSerializer = EphemeralDataUnitSerializer(mappings.ephemeralDataUnit)
    val persistentMessageDataUnitSerializer =
        PersistentMessageDataUnitSerializer(mappings.message, getRoomVersion)
    val persistentStateDataUnitSerializer =
        PersistentStateDataUnitSerializer(mappings.state, getRoomVersion)
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