package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import net.folivo.trixnity.core.model.events.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class UnsupportedEventContentTypeException(eventType: KClass<*>) : IllegalArgumentException(
    "Event content type $eventType is not supported. If it is a custom type, you should register it!"
)

fun <C : EventContent> Set<EventContentSerializerMapping<out C>>.fromClass(
    eventContentClass: KClass<out C>
): EventContentSerializerMapping<out C> =
    firstOrNull { it.kClass == eventContentClass }
        ?: throw UnsupportedEventContentTypeException(eventContentClass)

@JvmName("eventContentDeserializer")
fun Set<EventContentSerializerMapping<out EventContent>>.contentDeserializer(
    type: String,
): KSerializer<out EventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownEventContentSerializer(type)

@JvmName("eventContentSerializer")
fun Set<EventContentSerializerMapping<out EventContent>>.contentSerializer(
    content: EventContent,
): Pair<String, KSerializer<out EventContent>> =
    when (content) {
        is UnknownEventContent -> content.eventType to UnknownEventContentSerializer(content.eventType)
        else -> {
            val contentSerializerMapping =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentSerializerMapping.type to contentSerializerMapping.serializer
        }
    }

@JvmName("ephemeralEventContentDeserializer")
fun Set<EventContentSerializerMapping<out EphemeralEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out EphemeralEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownEphemeralEventContentSerializer(type)

@JvmName("ephemeralEventContentSerializer")
fun Set<EventContentSerializerMapping<out EphemeralEventContent>>.contentSerializer(
    content: EphemeralEventContent,
): Pair<String, KSerializer<out EphemeralEventContent>> =
    when (content) {
        is UnknownEphemeralEventContent -> content.eventType to UnknownEphemeralEventContentSerializer(content.eventType)
        else -> {
            val contentSerializerMapping =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentSerializerMapping.type to contentSerializerMapping.serializer
        }
    }

@JvmName("globalAccountDataEventContentDeserializer")
fun Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out GlobalAccountDataEventContent> =
    firstOrNull { type.startsWith(it.type) }?.serializer ?: UnknownGlobalAccountDataEventContentSerializer(type)

@JvmName("globalAccountDataEventContentSerializer")
fun Set<EventContentSerializerMapping<out GlobalAccountDataEventContent>>.contentSerializer(
    content: GlobalAccountDataEventContent,
): Pair<String, KSerializer<out GlobalAccountDataEventContent>> =
    when (content) {
        is UnknownGlobalAccountDataEventContent ->
            content.eventType to UnknownGlobalAccountDataEventContentSerializer(content.eventType)
        else -> {
            val contentSerializerMapping =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentSerializerMapping.type to contentSerializerMapping.serializer
        }
    }

@JvmName("roomAccountDataEventContentDeserializer")
fun Set<EventContentSerializerMapping<out RoomAccountDataEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out RoomAccountDataEventContent> =
    firstOrNull { type.startsWith(it.type) }?.serializer ?: UnknownRoomAccountDataEventContentSerializer(type)

@JvmName("roomAccountDataEventContentSerializer")
fun Set<EventContentSerializerMapping<out RoomAccountDataEventContent>>.contentSerializer(
    content: RoomAccountDataEventContent,
): Pair<String, KSerializer<out RoomAccountDataEventContent>> =
    when (content) {
        is UnknownRoomAccountDataEventContent ->
            content.eventType to UnknownRoomAccountDataEventContentSerializer(content.eventType)
        else -> {
            val contentSerializerMapping =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentSerializerMapping.type to contentSerializerMapping.serializer
        }
    }

@JvmName("roomEventContentDeserializer")
fun Set<EventContentSerializerMapping<out RoomEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out RoomEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownRoomEventContentSerializer(type)

@JvmName("roomEventContentSerializer")
fun Set<EventContentSerializerMapping<out RoomEventContent>>.contentSerializer(
    content: RoomEventContent,
): Pair<String, KSerializer<out RoomEventContent>> =
    when (content) {
        is UnknownRoomEventContent -> {
            content.eventType to UnknownRoomEventContentSerializer(content.eventType)
        }
        else -> {
            val contentDescriptor =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentDescriptor.type to contentDescriptor.serializer
        }
    }

@JvmName("stateEventContentDeserializer")
fun Set<EventContentSerializerMapping<out StateEventContent>>.contentDeserializer(
    type: String,
    isRedacted: Boolean,
): KSerializer<out StateEventContent> =
    if (!isRedacted) firstOrNull { it.type == type }?.serializer ?: UnknownStateEventContentSerializer(type)
    else RedactedStateEventContentSerializer(type)

@JvmName("stateEventContentSerializer")
fun Set<EventContentSerializerMapping<out StateEventContent>>.contentSerializer(
    content: StateEventContent,
): Pair<String, KSerializer<out StateEventContent>> =
    when (content) {
        is UnknownStateEventContent -> {
            content.eventType to UnknownStateEventContentSerializer(content.eventType)
        }
        is RedactedStateEventContent -> {
            content.eventType to RedactedStateEventContentSerializer(content.eventType)
        }
        else -> {
            val contentDescriptor = find { it.kClass.isInstance(content) }
            requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
            contentDescriptor.type to contentDescriptor.serializer
        }
    }

@JvmName("messageEventContentDeserializer")
fun Set<EventContentSerializerMapping<out MessageEventContent>>.contentDeserializer(
    type: String,
    isRedacted: Boolean,
): KSerializer<out MessageEventContent> =
    if (!isRedacted) firstOrNull { it.type == type }?.serializer ?: UnknownMessageEventContentSerializer(type)
    else RedactedMessageEventContentSerializer(type)

@JvmName("messageEventContentSerializer")
fun Set<EventContentSerializerMapping<out MessageEventContent>>.contentSerializer(
    content: MessageEventContent,
): Pair<String, KSerializer<out MessageEventContent>> =
    when (content) {
        is UnknownMessageEventContent -> {
            content.eventType to UnknownMessageEventContentSerializer(content.eventType)
        }
        is RedactedMessageEventContent -> {
            content.eventType to RedactedMessageEventContentSerializer(content.eventType)
        }
        else -> {
            val contentDescriptor =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentDescriptor.type to contentDescriptor.serializer
        }
    }

@JvmName("toDeviceEventContentDeserializer")
fun Set<EventContentSerializerMapping<out ToDeviceEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out ToDeviceEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownToDeviceEventContentSerializer(type)

@JvmName("toDeviceEventContentSerializer")
fun Set<EventContentSerializerMapping<out ToDeviceEventContent>>.contentSerializer(
    content: ToDeviceEventContent,
): Pair<String, KSerializer<out ToDeviceEventContent>> =
    when (content) {
        is UnknownToDeviceEventContent -> {
            content.eventType to UnknownToDeviceEventContentSerializer(content.eventType)
        }
        else -> {
            val contentDescriptor =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentDescriptor.type to contentDescriptor.serializer
        }
    }