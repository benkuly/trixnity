package net.folivo.trixnity.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.core.serialization.events.*


@OptIn(ExperimentalSerializationApi::class)
private fun createMatrixJson(
    module: SerializersModule
) = Json {
    classDiscriminator = "IfYouSeeThatSomethingIsWrongWithSerialization"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    serializersModule = module
}

fun createMatrixEventJson(
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings)
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixDataUnitJson(
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    customModule: SerializersModule? = null,
): Json {
    val modules = createDataUnitSerializersModule(
        eventContentSerializerMappings,
        roomVersionStore
    )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixEventAndDataUnitJson(
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings)
        .overwriteWith(
            createDataUnitSerializersModule(
                eventContentSerializerMappings,
                roomVersionStore
            )
        )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createDefaultEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings

fun createDefaultDataUnitContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultDataUnitContentSerializerMappings + customMappings
