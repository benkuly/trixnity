package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.m.PresenceDataUnitContent
import net.folivo.trixnity.core.serialization.events.SerializerMapping.Companion.of

val DefaultEphemeralDataUnitContentSerializerMappings: EphemeralDataUnitContentMappings = setOf(
    of<PresenceDataUnitContent>("m.presence")
)