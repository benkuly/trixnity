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
    val messageEventContentSerializer = MessageEventContentSerializer(mappings.message)
    val persistentMessageDataUnitSerializer =
        PersistentMessageDataUnitSerializer(mappings.message, messageEventContentSerializer, getRoomVersion)
    val stateEventContentSerializer = StateEventContentSerializer(mappings.state)
    val persistentStateDataUnitSerializer =
        PersistentStateDataUnitSerializer(mappings.state, stateEventContentSerializer, getRoomVersion)
    val persistentDataUnitSerializer =
        PersistentDataUnitSerializer(persistentMessageDataUnitSerializer, persistentStateDataUnitSerializer)
    return SerializersModule {
        contextual(ephemeralDataUnitSerializer)
        contextual(persistentMessageDataUnitSerializer)
        contextual(persistentStateDataUnitSerializer)
        contextual(persistentDataUnitSerializer)
    }
}