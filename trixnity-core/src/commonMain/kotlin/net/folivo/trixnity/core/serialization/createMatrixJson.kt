package net.folivo.trixnity.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.createEventSerializersModule
import net.folivo.trixnity.core.serialization.m.room.encrypted.createEncryptedEventContentSerializersModule
import net.folivo.trixnity.core.serialization.m.room.message.createMessageEventContentSerializersModule
import org.kodein.log.LoggerFactory

@OptIn(ExperimentalSerializationApi::class)
fun createMatrixJson(
    loggerFactory: LoggerFactory = LoggerFactory.default,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    customModule: SerializersModule? = null
): Json {
    val modules = (createEventSerializersModule(eventContentSerializerMappings, loggerFactory)
            + createMessageEventContentSerializersModule()
            + createEncryptedEventContentSerializersModule())

    return Json {
        classDiscriminator = "neverUsed"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        serializersModule =
            if (customModule != null) modules + customModule else modules
    }
}