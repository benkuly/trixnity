package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent

typealias GetRoomVersionFunction = (RoomId) -> String
typealias EphemeralDataUnitContentMappings = Set<SerializerMapping<out EphemeralDataUnitContent>>

fun createDataUnitSerializersModule(
    mappings: EventContentSerializerMappings,
    ephemeralMappings: EphemeralDataUnitContentMappings,
    getRoomVersion: GetRoomVersionFunction,
): SerializersModule {
    val ephemeralDataUnitSerializer = EphemeralDataUnitSerializer(ephemeralMappings)
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