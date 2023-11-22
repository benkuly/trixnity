package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import kotlin.reflect.KClass

open class EventContentToEventSerializerMappings<C : EventContent, E : Event<out C>, U : Event<UnknownEventContent>>(
    val baseMapping: Set<EventContentSerializerMapping<C>>,
    private val eventDeserializer: (EventContentSerializerMapping<C>) -> KSerializer<E>,
    private val eventSerializer: (EventContentSerializerMapping<C>) -> KSerializer<E> = eventDeserializer,
    private val unknownEventSerializer: (String) -> KSerializer<U>,
    private val typeField: String? = "type",
) {
    val eventDeserializers: Map<String, KSerializer<E>> by lazy {
        baseMapping.associate { it.type to eventDeserializer(it) }
    }

    data class SerializerWithType<E>(
        val type: String,
        val serializer: KSerializer<E>,
    )

    val eventSerializers: List<Pair<KClass<out C>, SerializerWithType<E>>> by lazy {
        baseMapping.map {
            val serializer = eventSerializer(it)
            it.kClass to SerializerWithType(
                it.type,
                if (typeField != null) AddFieldsSerializer(serializer, typeField to it.type)
                else serializer
            )
        }
    }

    operator fun get(type: String): KSerializer<E> = eventDeserializers[type]
        ?: @Suppress("UNCHECKED_CAST") (unknownEventSerializer(type) as KSerializer<E>)


    open operator fun get(content: C): SerializerWithType<E> {
        return eventSerializers.find { it.first.isInstance(content) }?.second
            ?: when (content) {
                is UnknownEventContent -> {
                    @Suppress("UNCHECKED_CAST")
                    val serializer = unknownEventSerializer(content.eventType) as KSerializer<E>
                    SerializerWithType(
                        content.eventType,
                        if (typeField != null) AddFieldsSerializer(serializer, typeField to content.eventType)
                        else serializer
                    )
                }

                else -> throw UnsupportedEventContentTypeException(content::class)
            }
    }
}

open class RoomEventContentToEventSerializerMappings<C : RoomEventContent, E : Event<out C>, U : Event<UnknownEventContent>, R : Event<RedactedEventContent>>(
    baseMapping: Set<EventContentSerializerMapping<C>>,
    eventDeserializer: (EventContentSerializerMapping<C>) -> KSerializer<E>,
    eventSerializer: (EventContentSerializerMapping<C>) -> KSerializer<E> = eventDeserializer,
    private val unknownEventSerializer: (String) -> KSerializer<U>,
    private val redactedEventSerializer: ((String) -> KSerializer<R>),
    private val typeField: String? = "type",
) : EventContentToEventSerializerMappings<C, E, U>(
    baseMapping = baseMapping,
    eventDeserializer = eventDeserializer,
    eventSerializer = eventSerializer,
    unknownEventSerializer = unknownEventSerializer,
    typeField = typeField
) {
    override operator fun get(content: C): SerializerWithType<E> {
        return eventSerializers.find { it.first.isInstance(content) }?.second
            ?: when (content) {
                is UnknownEventContent -> {
                    @Suppress("UNCHECKED_CAST")
                    val serializer = unknownEventSerializer(content.eventType) as KSerializer<E>
                    SerializerWithType(
                        content.eventType,
                        if (typeField != null) AddFieldsSerializer(serializer, typeField to content.eventType)
                        else serializer
                    )
                }

                is RedactedEventContent -> {
                    @Suppress("UNCHECKED_CAST")
                    val serializer = redactedEventSerializer(content.eventType) as KSerializer<E>
                    SerializerWithType(
                        content.eventType,
                        if (typeField != null) AddFieldsSerializer(serializer, typeField to content.eventType)
                        else serializer
                    )
                }

                else -> throw UnsupportedEventContentTypeException(content::class)
            }
    }
}