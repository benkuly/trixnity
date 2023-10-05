package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.Event.EphemeralEvent

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