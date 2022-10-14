package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.serialization.events.SerializerMapping.Companion.of

object DefaultEphemeralDataUnitContentSerializerMappings : BaseEventContentSerializerMappings() {
    override val ephemeralDataUnit: Set<SerializerMapping<out EphemeralDataUnitContent>> = setOf(
        of<PresenceDataUnitContent>("m.presence"),
        of<TypingDataUnitContent>("m.typing"),
        of<ReceiptDataUnitContent>("m.receipt"),
        of<DeviceListUpdateDataUnitContent>("m.device_list_update"),
        of<SigningKeyUpdateDataUnitContent>("m.signing_key_update"),
        of<DirectToDeviceDataUnitContent>("m.direct_to_device"),
    )
}