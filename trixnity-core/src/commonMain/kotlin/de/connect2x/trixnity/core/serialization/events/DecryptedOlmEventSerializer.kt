package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.DecryptedOlmEvent
import de.connect2x.trixnity.core.model.events.EventContent

class DecryptedOlmEventSerializer(
    eventContentSerializers: Set<EventContentSerializerMapping<EventContent>>,
) : BaseEventSerializer<EventContent, DecryptedOlmEvent<*>>(
    "DecryptedOlmEvent",
    EventContentToEventSerializerMappings(
        baseMapping = eventContentSerializers,
        eventDeserializer = { DecryptedOlmEvent.serializer(it.serializer) },
        unknownEventSerializer = { DecryptedOlmEvent.serializer(UnknownEventContentSerializer(it)) },
    )
)