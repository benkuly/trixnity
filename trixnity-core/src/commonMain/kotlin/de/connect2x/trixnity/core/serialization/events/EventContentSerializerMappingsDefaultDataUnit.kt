package de.connect2x.trixnity.core.serialization.events

private val eventContentSerializerMappingsDefaultDataUnit =
    EventContentSerializerMappings.default + EventContentSerializerMappings.defaultEphemeralDataUnit

val EventContentSerializerMappings.Companion.defaultDataUnit get() = eventContentSerializerMappingsDefaultDataUnit

fun EventContentSerializerMappings.Companion.defaultDataUnit(customMappings: EventContentSerializerMappings): EventContentSerializerMappings =
    EventContentSerializerMappings.defaultDataUnit + customMappings