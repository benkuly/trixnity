package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent

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