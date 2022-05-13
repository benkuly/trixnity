package net.folivo.trixnity.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.createDataUnitSerializersModule
import net.folivo.trixnity.core.serialization.events.createEventSerializersModule


@OptIn(ExperimentalSerializationApi::class)
fun createMatrixJson(
    module: SerializersModule
) = Json {
    classDiscriminator = "IfYouSeeThatSomethingIsWrongWithSerialization"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    serializersModule = module
}

fun createMatrixEventJson(
    eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings)
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixDataUnitJson(
    getRoomVersion: (RoomId) -> String,
    eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    customModule: SerializersModule? = null,
): Json {
    val modules = createDataUnitSerializersModule(eventContentSerializerMappings, getRoomVersion)
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixEventAndDataUnitJson(
    getRoomVersion: (RoomId) -> String,
    eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings) +
            createDataUnitSerializersModule(eventContentSerializerMappings, getRoomVersion)
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings