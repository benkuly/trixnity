package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass

interface EventContentSerializerMapping<C : EventContent> {
    val type: String
    val kClass: KClass<out C>
    val serializer: KSerializer<C>
}

class EventContentSerializerMappingImpl<C : EventContent>(
    override val type: String,
    override val kClass: KClass<out C>,
    serializer: KSerializer<out C>,
) : EventContentSerializerMapping<C> {
    override val serializer: KSerializer<C> =
        @Suppress("UNCHECKED_CAST")
        EventContentSerializer(type, serializer as KSerializer<C>)

    override fun toString(): String =
        "EventContentSerializerMapping(type=$type, kClass=$kClass, serializer=$serializer)"
}

class MessageEventContentSerializerMapping(
    override val type: String,
    override val kClass: KClass<out MessageEventContent>,
    serializer: KSerializer<out MessageEventContent>,
) : EventContentSerializerMapping<MessageEventContent> {
    override val serializer: KSerializer<MessageEventContent> = MessageEventContentSerializer(type, serializer)

    override fun toString(): String =
        "MessageEventContentSerializerMapping(type=$type, kClass=$kClass, serializer=$serializer)"
}

class StateEventContentSerializerMapping(
    override val type: String,
    override val kClass: KClass<out StateEventContent>,
    serializer: KSerializer<out StateEventContent>,
) : EventContentSerializerMapping<StateEventContent> {
    override val serializer: KSerializer<StateEventContent> = StateEventContentSerializer(type, serializer)

    override fun toString(): String =
        "StateEventContentSerializerMapping(type=$type, kClass=$kClass, serializer=$serializer)"
}