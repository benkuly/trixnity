package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.StateEventContent

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