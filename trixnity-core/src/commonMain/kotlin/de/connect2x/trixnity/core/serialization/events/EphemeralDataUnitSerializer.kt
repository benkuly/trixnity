package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.EphemeralDataUnit
import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent

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