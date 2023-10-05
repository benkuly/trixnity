package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

class ToDeviceEventSerializer(
    toDeviceEventContentSerializers: Set<EventContentSerializerMapping<ToDeviceEventContent>>,
) : BaseEventSerializer<ToDeviceEventContent, ToDeviceEvent<*>>(
    "ToDeviceEvent",
    EventContentToEventSerializerMappings(
        baseMapping = toDeviceEventContentSerializers,
        eventDeserializer = { ToDeviceEvent.serializer(it.serializer) },
        unknownEventSerializer = { ToDeviceEvent.serializer(UnknownEventContentSerializer(it)) },
    )
)