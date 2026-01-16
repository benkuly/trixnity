package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.StateEventContent

class InitialStateEventSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
) : BaseEventSerializer<StateEventContent, InitialStateEvent<*>>(
    "InitialStateEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { InitialStateEvent.serializer(it.serializer) },
        unknownEventSerializer = { InitialStateEvent.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { InitialStateEvent.serializer(RedactedEventContentSerializer(it)) },
    )
)