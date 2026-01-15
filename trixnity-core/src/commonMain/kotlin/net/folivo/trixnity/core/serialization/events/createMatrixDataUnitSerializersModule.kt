package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.block.EventContentBlocks

fun createMatrixDataUnitSerializersModule(
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
    val eventContentBlocksSerializer = EventContentBlocks.Serializer(mappings.block)
    val eventTypeSerializer = EventTypeSerializer(mappings)
    return SerializersModule {
        contextual(ephemeralDataUnitSerializer)
        contextual(persistentMessageDataUnitSerializer)
        contextual(persistentStateDataUnitSerializer)
        contextual(persistentDataUnitSerializer)
        contextual(eventContentBlocksSerializer)
        contextual(eventTypeSerializer)
    }
}