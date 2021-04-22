package net.folivo.trixnity.core.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.event.DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.event.createEventSerializersModule
import net.folivo.trixnity.core.serialization.m.room.message.createMessageEventContentSerializersModule

fun createJson(
    roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>> = DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS,
    stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>> = DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS,
    customModule: SerializersModule? = null
): Json {
    val modules = createEventSerializersModule(
        roomEventContentSerializers,
        stateEventContentSerializers
    ) + createMessageEventContentSerializersModule()

    return Json {
        classDiscriminator = "neverUsed"
        ignoreUnknownKeys = true
        serializersModule =
            if (customModule != null) modules + customModule else modules
    }
}