package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

data class SerializerMapping<C : Any>(
    val type: String,
    val kClass: KClass<C>,
    val serializer: KSerializer<C>
) {
    companion object {
        inline fun <reified C : Any> of(
            type: String,
            serializer: KSerializer<C>
        ): SerializerMapping<C> {
            return SerializerMapping(type, C::class, serializer)
        }

        inline fun <reified C : Any> of(
            type: String
        ): SerializerMapping<C> {
            return SerializerMapping(type, C::class, serializer())
        }
    }
}