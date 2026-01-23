package de.connect2x.trixnity.client.notification

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val log = Logger("de.connect2x.trixnity.client.notification.jsonPath")

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