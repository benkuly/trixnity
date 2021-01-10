package net.folivo.trixnity.core.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownEvent
import net.folivo.trixnity.core.model.events.m.room.*
import kotlin.reflect.KClass

class EventSerializer(customSerializers: Map<String, EventSerializerDescriptor<out Event<*>>> = mapOf()) :
    JsonContentPolymorphicSerializer<Event<*>>(Event::class) {

    data class EventSerializerDescriptor<T : Event<*>>(
        val kclass: KClass<T>,
        val serializer: KSerializer<T>
    )

    companion object {
        val defaultSerializers = mapOf(
            "m.room.avatar" to EventSerializerDescriptor(
                AvatarEvent::class,
                AvatarEvent.serializer()
            ),
            "m.room.canonical_alias" to EventSerializerDescriptor(
                CanonicalAliasEvent::class,
                CanonicalAliasEvent.serializer()
            ),
            "m.room.create" to EventSerializerDescriptor(
                CreateEvent::class,
                CreateEvent.serializer()
            ),
            "m.room.join_rules" to EventSerializerDescriptor(
                JoinRulesEvent::class,
                JoinRulesEvent.serializer()
            ),
            "m.room.member" to EventSerializerDescriptor(
                MemberEvent::class,
                MemberEvent.serializer()
            ),
            "m.room.message" to EventSerializerDescriptor(
                MessageEvent::class,
                MessageEvent.serializer()
            ),
            "m.room.name" to EventSerializerDescriptor(
                NameEvent::class,
                NameEvent.serializer()
            ),
            "m.room.pinned_events" to EventSerializerDescriptor(
                PinnedEventsEvent::class,
                PinnedEventsEvent.serializer()
            ),
            "m.room.power_levels" to EventSerializerDescriptor(
                PowerLevelsEvent::class,
                PowerLevelsEvent.serializer()
            ),
            "m.room.redaction" to EventSerializerDescriptor(
                RedactionEvent::class,
                RedactionEvent.serializer()
            ),
            "m.room.topic" to EventSerializerDescriptor(
                TopicEvent::class,
                TopicEvent.serializer()
            ),
        )
    }

    private val serializers = defaultSerializers + customSerializers

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Event<*>> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        if (type != null) {
            val matchingSerializer = serializers[type]?.serializer
            if (matchingSerializer != null) {
                return matchingSerializer
            }
        }
        return UnknownEvent.serializer()
    }
}