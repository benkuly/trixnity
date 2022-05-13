package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.RoomId

fun createDataUnitSerializersModule(
    mappings: EventContentSerializerMappings,
    getRoomVersion: (RoomId) -> String,
): SerializersModule {
    val ephemeralDataUnitSerializer = EphemeralDataUnitSerializer(mappings.ephemeral)
    val persistentMessageDataUnitSerializer = PersistentMessageDataUnitSerializer(mappings.message, getRoomVersion)
    val persistentStateDataUnitSerializer = PersistentStateDataUnitSerializer(mappings.state, getRoomVersion)
    val persistentDataUnitSerializer =
        PersistentDataUnitSerializer(persistentMessageDataUnitSerializer, persistentStateDataUnitSerializer)
    return SerializersModule {
        contextual(ephemeralDataUnitSerializer)
        contextual(persistentMessageDataUnitSerializer)
        contextual(persistentStateDataUnitSerializer)
        contextual(persistentDataUnitSerializer)
    }
}