package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.m.*

val DefaultEphemeralDataUnitContentSerializerMappings = createEventContentSerializerMappings {
    ephemeralDataUnitOf<PresenceDataUnitContent>("m.presence")
    ephemeralDataUnitOf<TypingDataUnitContent>("m.typing")
    ephemeralDataUnitOf<ReceiptDataUnitContent>("m.receipt")
    ephemeralDataUnitOf<DeviceListUpdateDataUnitContent>("m.device_list_update")
    ephemeralDataUnitOf<SigningKeyUpdateDataUnitContent>("m.signing_key_update")
    ephemeralDataUnitOf<DirectToDeviceDataUnitContent>("m.direct_to_device")
}