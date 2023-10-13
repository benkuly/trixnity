package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.EventContent

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