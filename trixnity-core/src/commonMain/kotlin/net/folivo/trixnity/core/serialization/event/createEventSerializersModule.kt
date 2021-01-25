package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.Event.UnknownEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass

fun createEventSerializersModule(
    roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
    stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>
): SerializersModule {
    val roomEventSerializer = RoomEventSerializer(roomEventContentSerializers)
    val stateEventSerializer = StateEventSerializer(stateEventContentSerializers)
    val strippedStateEventSerializer = StrippedStateEventSerializer(stateEventContentSerializers)
    val eventSerializer = EventSerializer(
        roomEventSerializer,
        stateEventSerializer,
        strippedStateEventSerializer,
        roomEventContentSerializers,
        stateEventContentSerializers
    )
    return SerializersModule {
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(stateEventSerializer)
        contextual(stateEventSerializer)
        contextual(UnknownEvent.serializer())

//        polymorphic(Event::class, eventSerializer) {
//            subclass(roomEventSerializer)
//            subclass(stateEventSerializer)
//            subclass(strippedStateEventSerializer)
//            subclass(UnknownEvent.serializer())
//        }

        roomEventContentSerializers.forEach {
            contextual(it.kClass as KClass<RoomEventContent>, it.serializer as KSerializer<RoomEventContent>)
        }
        stateEventContentSerializers.forEach {
            contextual(it.kClass as KClass<StateEventContent>, it.serializer as KSerializer<StateEventContent>)
        }

    }
}