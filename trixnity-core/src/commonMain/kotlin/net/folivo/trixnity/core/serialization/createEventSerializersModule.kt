package net.folivo.trixnity.core.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
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
        contextual(Event::class, object : JsonContentPolymorphicSerializer<Event<*>>(Event::class) {
            override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Event<*>> {
                val type = element.jsonObject["type"]?.jsonPrimitive?.content
                return eventsLookup[type] ?: UnknownEvent.serializer()
            }
        })
        contextual(RoomEvent::class, object : JsonContentPolymorphicSerializer<RoomEvent<*>>(RoomEvent::class) {
            override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out RoomEvent<*>> {
                val type = element.jsonObject["type"]?.jsonPrimitive?.content
                return roomEventsLookup[type] ?: UnknownRoomEvent.serializer()
            }
        })
        contextual(StateEvent::class, object : JsonContentPolymorphicSerializer<StateEvent<*>>(StateEvent::class) {
            override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out StateEvent<*>> {
                val type = element.jsonObject["type"]?.jsonPrimitive?.content
                return stateEventsLookup[type] ?: UnknownStateEvent.serializer()
            }
        })
        polymorphic(Event::class) {
            default { eventsLookup[it] ?: UnknownEvent.serializer() }
        }
        polymorphic(RoomEvent::class) {
            default { roomEventsLookup[it] ?: UnknownRoomEvent.serializer() }
        }
        polymorphic(StateEvent::class) {
            default { stateEventsLookup[it] ?: UnknownStateEvent.serializer() }
        }
    }
}