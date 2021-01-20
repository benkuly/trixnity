package net.folivo.trixnity.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import net.folivo.trixnity.core.model.events.*

fun createEventSerializersModule(
    roomEventSerializers: Set<EventSerializerDescriptor<out RoomEvent<*>, *>> = defaultRoomEventSerializers,
    stateEventSerializers: Set<EventSerializerDescriptor<out StateEvent<*>, *>> = defaultStateEventSerializers
): SerializersModule {
    val eventsLookup: Map<String, KSerializer<out Event<*>>> =
        (roomEventSerializers + stateEventSerializers).map { Pair(it.type, it.serializer) }.toMap()
    val roomEventsLookup = roomEventSerializers.map { Pair(it.type, it.serializer) }.toMap()
    val stateEventsLookup = stateEventSerializers.map { Pair(it.type, it.serializer) }.toMap()
    return SerializersModule {
        // TODO remove when https://github.com/Kotlin/kotlinx.serialization/issues/944 is fixed
        contextual(Any::class, object : KSerializer<Any> {
            private val exception = SerializationException(
                "Any is never expected to be serialized. " +
                        "The serializer is only defined since the compiler does not know this, causing a compilation error."
            )
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("This should never be serialized.")
            override fun deserialize(decoder: Decoder): Any = throw exception
            override fun serialize(encoder: Encoder, value: Any) = throw exception
        })
        polymorphicDefault(Event::class) {
            eventsLookup[it] ?: UnknownEvent.serializer()
        }
        polymorphicDefault(RoomEvent::class) {
            roomEventsLookup[it] ?: UnknownRoomEvent.serializer()
        }
        polymorphicDefault(StateEvent::class) {
            stateEventsLookup[it] ?: UnknownStateEvent.serializer()
        }
    }
}