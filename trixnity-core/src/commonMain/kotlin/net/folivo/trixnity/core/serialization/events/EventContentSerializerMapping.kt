package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.model.events.EventContent
import kotlin.reflect.KClass

data class EventContentSerializerMapping<C : EventContent>(
    val type: String,
    val kClass: KClass<C>,
    val serializer: KSerializer<C>
) {
    companion object {
        inline fun <reified C : EventContent> of(
            type: String,
            serializer: KSerializer<C>
        ): EventContentSerializerMapping<C> {
            return EventContentSerializerMapping(type, C::class, serializer)
        }

        inline fun <reified C : EventContent> of(
            type: String
        ): EventContentSerializerMapping<C> {
            return EventContentSerializerMapping(type, C::class, serializer())
        }
    }
}