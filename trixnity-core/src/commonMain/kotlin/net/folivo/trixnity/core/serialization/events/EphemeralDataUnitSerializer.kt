package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent

class EphemeralDataUnitSerializer(
    ephemeralDataUnitContentSerializers: Set<EventContentSerializerMapping<EphemeralDataUnitContent>>,
) : BaseEventSerializer<EphemeralDataUnitContent, EphemeralDataUnit<*>>(
    "EphemeralDataUnit",
    EventContentToEventSerializerMappings(
        baseMapping = ephemeralDataUnitContentSerializers,
        eventDeserializer = { EphemeralDataUnit.serializer(it.serializer) },
        unknownEventSerializer = { EphemeralDataUnit.serializer(UnknownEventContentSerializer(it)) },
        typeField = "edu_type",
    ),
    "edu_type"
)