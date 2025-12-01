package net.folivo.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class LocalizedField<T>(val default: T? = null, val translations: Map<String, T>? = null)

@OptIn(ExperimentalSerializationApi::class)
abstract class LocalizedObjectSerializer<T>(delegate: KSerializer<T>) :
    JsonTransformingSerializer<T>(delegate) {
    private val localizedFields =
        buildSet {
            repeat(delegate.descriptor.elementsCount) { index ->
                if (delegate.descriptor.getElementDescriptor(index).serialName == LocalizedField::class.qualifiedName) {
                    add(delegate.descriptor.getElementName(index))
                }
            }
        }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element.filterKeys { key -> key !in localizedFields && key.substringBefore("#") !in localizedFields })
            localizedFields.forEach { localizedField ->
                put(localizedField, buildJsonObject {
                    element[localizedField]?.let { put("default", it) }
                    val translations = element
                        .filterKeys { key -> key.contains('#') && key.substringBefore('#') == localizedField }
                        .map { (key, value) ->
                            key.substringAfter('#') to value
                        }.takeIf { it.isNotEmpty() }?.toMap()
                    translations?.let { put("translations", JsonObject(it)) }
                })
            }
        })
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element.filterKeys { it !in localizedFields })
            localizedFields.associateWith { element[it] as? JsonObject }.forEach { (key, value) ->
                if (value == null) return@forEach
                val default = value["default"]
                val translations = value["translations"]
                if (default != null) put(key, default)
                if (translations != null && translations !is JsonNull) {
                    require(translations is JsonObject) { "translations must be a JsonObject but was ${translations::class.simpleName}" }
                    translations.forEach { (language, value) ->
                        put("$key#$language", value)
                    }
                }
            }
        })
    }
}