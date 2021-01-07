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

class EventSerializer(customSerializers: Map<String, KSerializer<out Event<*>>> = mapOf()) :
    JsonContentPolymorphicSerializer<Event<*>>(Event::class) {

    companion object {
        val defaultSerializers = mapOf(
            "m.room.avatar" to AvatarEvent.serializer(),
            "m.room.canonical_alias" to CanonicalAliasEvent.serializer(),
            "m.room.create" to CreateEvent.serializer(),
            "m.room.join_rules" to JoinRulesEvent.serializer(),
            "m.room.member" to MemberEvent.serializer(),
            "m.room.message" to MessageEvent.serializer(MessageEventContentSerializer()),
            "m.room.name" to NameEvent.serializer(),
            "m.room.pinned_events" to PinnedEventsEvent.serializer(),
            "m.room.power_levels" to PowerLevelsEvent.serializer(),
            "m.room.redaction" to RedactionEvent.serializer(),
            "m.room.topic" to TopicEvent.serializer(),
        )
    }

    private val serializers = defaultSerializers + customSerializers

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Event<*>> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
        if (type != null) {
            val matchingSerializer = serializers[type]
            if (matchingSerializer != null) {
                return matchingSerializer
            }
        }
        return UnknownEvent.serializer()
    }
}