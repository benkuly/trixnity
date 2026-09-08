package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.model.events.DelayedEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent

@MSC4140
class DelayedMessageEventSerializer(
    messageEventContentSerializers: Set<EventContentSerializerMapping<MessageEventContent>>
) :
    BaseEventSerializer<MessageEventContent, DelayedEvent.DelayedMessageEvent<*>>(
        "DelayedEvent.DelayedMessageEvent",
        RoomEventContentToEventSerializerMappings(
            baseMapping = messageEventContentSerializers,
            eventDeserializer = { DelayedEvent.DelayedMessageEvent.serializer(it.serializer) },
            unknownEventSerializer = { DelayedEvent.DelayedMessageEvent.serializer(UnknownEventContentSerializer(it)) },
            redactedEventSerializer = {
                DelayedEvent.DelayedMessageEvent.serializer(RedactedEventContentSerializer(it))
            },
        ),
    )
