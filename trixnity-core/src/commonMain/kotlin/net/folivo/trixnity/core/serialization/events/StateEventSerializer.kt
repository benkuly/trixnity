package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.StateEventContent

class StateEventSerializer(
    stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>,
) : BaseEventSerializer<StateEventContent, StateEvent<*>>(
    "StateEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = stateEventContentSerializers,
        eventDeserializer = { StateEvent.serializer(it.serializer) },
        unknownEventSerializer = { StateEvent.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { StateEvent.serializer(RedactedEventContentSerializer(it)) },
    )
)