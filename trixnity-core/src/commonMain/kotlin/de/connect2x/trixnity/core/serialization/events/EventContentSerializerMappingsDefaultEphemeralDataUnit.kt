package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.m.*

private val eventContentSerializerMappingsDefaultEphemeralDataUnit = EventContentSerializerMappings {
    ephemeralDataUnitOf<PresenceDataUnitContent>("m.presence")
    ephemeralDataUnitOf<TypingDataUnitContent>("m.typing")
    ephemeralDataUnitOf<ReceiptDataUnitContent>("m.receipt")
    ephemeralDataUnitOf<DeviceListUpdateDataUnitContent>("m.device_list_update")
    ephemeralDataUnitOf<SigningKeyUpdateDataUnitContent>("m.signing_key_update")
    ephemeralDataUnitOf<DirectToDeviceDataUnitContent>("m.direct_to_device")
}

val EventContentSerializerMappings.Companion.defaultEphemeralDataUnit get() = eventContentSerializerMappingsDefaultEphemeralDataUnit

fun EventContentSerializerMappings.Companion.defaultEphemeralDataUnit(customMappings: EventContentSerializerMappings): EventContentSerializerMappings =
    EventContentSerializerMappings.defaultDataUnit + customMappings