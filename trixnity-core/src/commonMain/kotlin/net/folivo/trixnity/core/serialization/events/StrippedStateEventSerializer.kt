package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.StateEventContent

class StrippedStateEventSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
) : BaseEventSerializer<StateEventContent, StrippedStateEvent<*>>(
    "StrippedStateEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { StrippedStateEvent.serializer(it.serializer) },
        unknownEventSerializer = { StrippedStateEvent.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { StrippedStateEvent.serializer(RedactedEventContentSerializer(it)) },
    )
)