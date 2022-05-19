package net.folivo.trixnity.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.core.serialization.events.*


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
    getRoomVersion: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    ephemeralDateUnitContentSerializerMappings: EphemeralDataUnitContentMappings = createEphemeralDateUnitContentSerializerMappings(),
    customModule: SerializersModule? = null,
): Json {
    val modules = createDataUnitSerializersModule(
        eventContentSerializerMappings,
        ephemeralDateUnitContentSerializerMappings,
        getRoomVersion
    )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createMatrixEventAndDataUnitJson(
    getRoomVersion: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    ephemeralDateUnitContentSerializerMappings: EphemeralDataUnitContentMappings = createEphemeralDateUnitContentSerializerMappings(),
    customModule: SerializersModule? = null,
): Json {
    val modules = createEventSerializersModule(eventContentSerializerMappings) +
            createDataUnitSerializersModule(
                eventContentSerializerMappings,
                ephemeralDateUnitContentSerializerMappings,
                getRoomVersion
            )
    return createMatrixJson(if (customModule != null) modules + customModule else modules)
}

fun createEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings

fun createEphemeralDateUnitContentSerializerMappings(customMappings: EphemeralDataUnitContentMappings? = null): EphemeralDataUnitContentMappings =
    DefaultEphemeralDataUnitContentSerializerMappings + customMappings.orEmpty()
