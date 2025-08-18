package net.folivo.trixnity.client.notification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val log = KotlinLogging.logger("net.folivo.trixnity.client.notification.jsonPath")

//language=RegExp
private val dotRegex = """(?<!\\)(?:\\\\)*[.]""".toRegex()

//language=RegExp
private val removeEscapes = """\\([.\\])""".toRegex()
internal fun jsonPath(
    value: JsonObject,
    key: String
): JsonElement? {
    return try {
        var targetProperty: JsonElement? = value
        key.split(dotRegex)
            .map { it.replace(removeEscapes, "$1") }
            .forEach { segment ->
                targetProperty = (targetProperty as? JsonObject)?.get(segment)
            }
        targetProperty
    } catch (exc: Exception) {
        log.warn(exc) { "could not find event property for key $key in event $value" }
        null
    }
}