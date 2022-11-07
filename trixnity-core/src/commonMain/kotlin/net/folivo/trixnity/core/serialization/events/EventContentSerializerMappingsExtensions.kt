package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import net.folivo.trixnity.core.model.events.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class UnsupportedEventContentTypeException(eventType: KClass<*>) : IllegalArgumentException(
    "Event content type $eventType is not supported. If it is a custom type, you should register it!"
)

fun <C : EventContent> Set<SerializerMapping<out C>>.fromClass(
    eventContentClass: KClass<out C>
): SerializerMapping<out C> =
    firstOrNull { it.kClass == eventContentClass }
        ?: throw UnsupportedEventContentTypeException(eventContentClass)

@JvmName("eventContentDeserializer")
fun Set<SerializerMapping<out EventContent>>.contentDeserializer(
    type: String,
): KSerializer<out EventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownEventContentSerializer(type)

@JvmName("eventContentSerializer")
fun Set<SerializerMapping<out EventContent>>.contentSerializer(
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
fun Set<SerializerMapping<out EphemeralEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out EphemeralEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownEphemeralEventContentSerializer(type)

@JvmName("ephemeralEventContentSerializer")
fun Set<SerializerMapping<out EphemeralEventContent>>.contentSerializer(
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

@JvmName("ephemeralDataUnitContentDeserializer")
fun Set<SerializerMapping<out EphemeralDataUnitContent>>.contentDeserializer(
    type: String,
): KSerializer<out EphemeralDataUnitContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownEphemeralDataUnitContentSerializer(type)

@JvmName("globalAccountDataEventContentDeserializer")
fun Set<SerializerMapping<out GlobalAccountDataEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out GlobalAccountDataEventContent> =
    firstOrNull { type.startsWith(it.type) }?.serializer ?: UnknownGlobalAccountDataEventContentSerializer(type)

@JvmName("globalAccountDataEventContentSerializer")
fun Set<SerializerMapping<out GlobalAccountDataEventContent>>.contentSerializer(
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
fun Set<SerializerMapping<out RoomAccountDataEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out RoomAccountDataEventContent> =
    firstOrNull { type.startsWith(it.type) }?.serializer ?: UnknownRoomAccountDataEventContentSerializer(type)

@JvmName("roomAccountDataEventContentSerializer")
fun Set<SerializerMapping<out RoomAccountDataEventContent>>.contentSerializer(
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
fun Set<SerializerMapping<out RoomEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out RoomEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownRoomEventContentSerializer(type)

@JvmName("roomEventContentSerializer")
fun Set<SerializerMapping<out RoomEventContent>>.contentSerializer(
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
fun Set<SerializerMapping<out StateEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out StateEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownStateEventContentSerializer(type)

@JvmName("stateEventContentType")
fun Set<SerializerMapping<out StateEventContent>>.contentType(
    content: StateEventContent,
): String =
    when (content) {
        is UnknownStateEventContent -> {
            content.eventType
        }

        is RedactedStateEventContent -> {
            content.eventType
        }

        else -> {
            val contentDescriptor = find { it.kClass.isInstance(content) }
            requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
            contentDescriptor.type
        }
    }

@JvmName("stateEventContentSerializer")
fun Set<SerializerMapping<out StateEventContent>>.contentSerializer(
    content: StateEventContent,
): KSerializer<out StateEventContent> =
    when (content) {
        is UnknownStateEventContent -> {
            UnknownStateEventContentSerializer(content.eventType)
        }

        is RedactedStateEventContent -> {
            RedactedStateEventContentSerializer(content.eventType)
        }

        else -> {
            val contentDescriptor = find { it.kClass.isInstance(content) }
            requireNotNull(contentDescriptor) { "event content type ${content::class} must be registered" }
            contentDescriptor.serializer
        }
    }

@JvmName("messageEventContentDeserializer")
fun Set<SerializerMapping<out MessageEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out MessageEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownMessageEventContentSerializer(type)

@JvmName("messageEventContentType")
fun Set<SerializerMapping<out MessageEventContent>>.contentType(
    content: MessageEventContent,
): String =
    when (content) {
        is UnknownMessageEventContent -> {
            content.eventType
        }

        is RedactedMessageEventContent -> {
            content.eventType
        }

        else -> {
            val contentDescriptor =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentDescriptor.type
        }
    }

@JvmName("messageEventContentSerializer")
fun Set<SerializerMapping<out MessageEventContent>>.contentSerializer(
    content: MessageEventContent,
): KSerializer<out MessageEventContent> =
    when (content) {
        is UnknownMessageEventContent -> {
            UnknownMessageEventContentSerializer(content.eventType)
        }

        is RedactedMessageEventContent -> {
            RedactedMessageEventContentSerializer(content.eventType)
        }

        else -> {
            val contentDescriptor =
                find { it.kClass.isInstance(content) } ?: throw UnsupportedEventContentTypeException(content::class)
            contentDescriptor.serializer
        }
    }

@JvmName("toDeviceEventContentDeserializer")
fun Set<SerializerMapping<out ToDeviceEventContent>>.contentDeserializer(
    type: String,
): KSerializer<out ToDeviceEventContent> =
    firstOrNull { it.type == type }?.serializer ?: UnknownToDeviceEventContentSerializer(type)

@JvmName("toDeviceEventContentSerializer")
fun Set<SerializerMapping<out ToDeviceEventContent>>.contentSerializer(
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