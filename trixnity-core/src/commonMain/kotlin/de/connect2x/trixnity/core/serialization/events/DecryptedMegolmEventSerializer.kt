package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.DecryptedMegolmEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent

class DecryptedMegolmEventSerializer(
    messageEventContentSerializers: Set<EventContentSerializerMapping<MessageEventContent>>,
) : BaseEventSerializer<MessageEventContent, DecryptedMegolmEvent<*>>(
    "DecryptedMegolmEvent",
    RoomEventContentToEventSerializerMappings(
        baseMapping = messageEventContentSerializers,
        eventDeserializer = { DecryptedMegolmEvent.serializer(it.serializer) },
        unknownEventSerializer = { DecryptedMegolmEvent.serializer(UnknownEventContentSerializer(it)) },
        redactedEventSerializer = { DecryptedMegolmEvent.serializer(RedactedEventContentSerializer(it)) },
    )
)