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
    getRoomVersion: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    customModule: SerializersModule? = null,
): Json {
    val modules = createDataUnitSerializersModule(
        eventContentSerializerMappings,
        getRoomVersion
    )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixEventAndDataUnitJson(
    getRoomVersion: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings)
        .overwriteWith(
            createDataUnitSerializersModule(
                eventContentSerializerMappings,
                getRoomVersion
            )
        )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings

fun createDataUnitContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultDataUnitContentSerializerMappings + customMappings
