package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.DecryptedMegolmEvent
import net.folivo.trixnity.core.model.events.MessageEventContent

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