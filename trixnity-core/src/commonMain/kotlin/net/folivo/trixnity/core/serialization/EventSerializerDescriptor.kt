package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import net.folivo.trixnity.core.model.events.Event
import kotlin.reflect.KClass

data class EventSerializerDescriptor<T : Event<C>, C : Any>(
    val type: String,
    val eventClass: KClass<T>,
    val eventContentClass: KClass<C>,
    val serializer: KSerializer<T>
) {
    companion object {
        inline fun <reified T : Event<C>, reified C : Any> of(
            type: String,
            serializer: KSerializer<T>
        ): EventSerializerDescriptor<T, C> {
            return EventSerializerDescriptor(type, T::class, C::class, serializer)
        }
    }
}