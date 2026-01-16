package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import de.connect2x.trixnity.core.model.events.block.EventContentBlock
import kotlin.reflect.KClass

interface EventContentBlockSerializerMapping<C : EventContentBlock> {
    val type: EventContentBlock.Type<C>
    val kClass: KClass<out C>
    val serializer: KSerializer<C>
}

data class EventContentBlockSerializerMappingImpl<C : EventContentBlock>(
    override val type: EventContentBlock.Type<C>,
    override val kClass: KClass<out C>,
    override val serializer: KSerializer<C>,
) : EventContentBlockSerializerMapping<C>