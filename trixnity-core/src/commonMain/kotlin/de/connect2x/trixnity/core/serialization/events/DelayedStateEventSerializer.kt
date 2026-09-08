package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.model.events.DelayedEvent
import de.connect2x.trixnity.core.model.events.StateEventContent

@MSC4140
class DelayedStateEventSerializer(stateEventContentSerializers: Set<EventContentSerializerMapping<StateEventContent>>) :
    BaseEventSerializer<StateEventContent, DelayedEvent.DelayedStateEvent<*>>(
        "DelayedEvent.DelayedStateEvent",
        RoomEventContentToEventSerializerMappings(
            baseMapping = stateEventContentSerializers,
            eventDeserializer = { DelayedEvent.DelayedStateEvent.serializer(it.serializer) },
            unknownEventSerializer = { DelayedEvent.DelayedStateEvent.serializer(UnknownEventContentSerializer(it)) },
            redactedEventSerializer = { DelayedEvent.DelayedStateEvent.serializer(RedactedEventContentSerializer(it)) },
        ),
    )
