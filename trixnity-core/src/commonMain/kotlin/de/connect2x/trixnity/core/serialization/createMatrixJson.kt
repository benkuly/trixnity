package de.connect2x.trixnity.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.modules.plus
import de.connect2x.trixnity.core.serialization.events.*

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
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    customModule: SerializersModule? = null,
): Json {
    val modules = createMatrixEventSerializersModule(eventContentSerializerMappings)
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixDataUnitJson(
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.defaultDataUnit,
    customModule: SerializersModule? = null,
): Json {
    val modules = createMatrixDataUnitSerializersModule(
        eventContentSerializerMappings,
        roomVersionStore
    )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixEventAndDataUnitJson(
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.defaultDataUnit,
    customModule: SerializersModule? = null,
): Json {
    val modules = createMatrixEventSerializersModule(eventContentSerializerMappings)
        .overwriteWith(
            createMatrixDataUnitSerializersModule(
                eventContentSerializerMappings,
                roomVersionStore
            )
        )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}