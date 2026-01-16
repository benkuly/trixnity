package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.ClientEvent.EphemeralEvent
import de.connect2x.trixnity.core.model.events.EphemeralEventContent

class EphemeralEventSerializer(
    ephemeralEventContentSerializers: Set<EventContentSerializerMapping<EphemeralEventContent>>,
) : BaseEventSerializer<EphemeralEventContent, EphemeralEvent<*>>(
    "EphemeralEvent",
    EventContentToEventSerializerMappings(
        baseMapping = ephemeralEventContentSerializers,
        eventDeserializer = { EphemeralEvent.serializer(it.serializer) },
        unknownEventSerializer = { EphemeralEvent.serializer(UnknownEventContentSerializer(it)) },
    )
)