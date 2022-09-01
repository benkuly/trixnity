package net.folivo.trixnity.core.serialization.events

import net.folivo.trixnity.core.model.events.EphemeralEventContent

val DefaultDataUnitContentSerializerMappings =
    (DefaultEventContentSerializerMappings - object : BaseEventContentSerializerMappings() {
        override val ephemeral: Set<SerializerMapping<out EphemeralEventContent>> =
            DefaultEventContentSerializerMappings.ephemeral
    }) + DefaultEphemeralDataUnitContentSerializerMappings