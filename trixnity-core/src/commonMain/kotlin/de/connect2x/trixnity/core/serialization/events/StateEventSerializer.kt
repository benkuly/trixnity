package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.StateEventContent

class StateEventSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
) : BaseEventSerializer<StateEventContent, StateEvent<*>>(
    "StateEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { PutTypeIntoPrevContentSerializer(StateEvent.serializer(it.serializer)) },
        unknownEventSerializer = {
            PutTypeIntoPrevContentSerializer(StateEvent.serializer(UnknownEventContentSerializer(it)))
        },
        redactedEventSerializer = {
            PutTypeIntoPrevContentSerializer(StateEvent.serializer(RedactedEventContentSerializer(it)))
        },
    )
)