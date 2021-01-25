package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass

fun createEventSerializersModule(
    roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>>,
    stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>>
): SerializersModule {
    val basicEventSerializer = BasicEventSerializer()
    val roomEventSerializer = RoomEventSerializer(roomEventContentSerializers)
    val stateEventSerializer = StateEventSerializer(stateEventContentSerializers)
    val strippedStateEventSerializer = StrippedStateEventSerializer(stateEventContentSerializers)
    val eventSerializer = EventSerializer(
        basicEventSerializer,
        roomEventSerializer,
        stateEventSerializer,
        strippedStateEventSerializer
    )
    return SerializersModule {
        contextual(basicEventSerializer)
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(stateEventSerializer)
        contextual(stateEventSerializer)

        polymorphic(Event::class, eventSerializer) {
            subclass(basicEventSerializer)
            subclass(roomEventSerializer)
            subclass(stateEventSerializer)
            subclass(strippedStateEventSerializer)
        }

        roomEventContentSerializers.forEach {
            contextual(it.kClass as KClass<RoomEventContent>, it.serializer as KSerializer<RoomEventContent>)
        }
        stateEventContentSerializers.forEach {
            contextual(it.kClass as KClass<StateEventContent>, it.serializer as KSerializer<StateEventContent>)
        }

        polymorphic(EventContent::class) {
            (roomEventContentSerializers + stateEventContentSerializers).forEach {
                subclass(it.kClass as KClass<EventContent>, it.serializer as KSerializer<EventContent>)
            }
        }
        polymorphic(RoomEventContent::class) {
            roomEventContentSerializers.forEach {
                subclass(it.kClass as KClass<RoomEventContent>, it.serializer as KSerializer<RoomEventContent>)
            }
        }
        polymorphic(StateEventContent::class) {
            stateEventContentSerializers.forEach {
                subclass(it.kClass as KClass<StateEventContent>, it.serializer as KSerializer<StateEventContent>)
            }
        }


    }
}