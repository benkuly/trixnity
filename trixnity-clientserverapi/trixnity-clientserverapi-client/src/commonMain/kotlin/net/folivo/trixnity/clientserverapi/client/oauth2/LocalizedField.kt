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

    private data class LocalizedFieldData(
        val name: String,
        val isNullable: Boolean
    )

    private val localizedFieldsData: Map<String, LocalizedFieldData> =
        buildMap {
            repeat(delegate.descriptor.elementsCount) { index ->
                val elementDescriptor = delegate.descriptor.getElementDescriptor(index)
                val serialName = elementDescriptor.serialName.substringAfterLast('.')
                if (serialName.removeSuffix("?") == LocalizedField::class.simpleName) {
                    val elementName = delegate.descriptor.getElementName(index)
                    put(
                        elementName,
                        LocalizedFieldData(
                            name = elementName,
                            isNullable = serialName.endsWith('?')
                        )
                    )
                }
            }
        }
    private val localizedField: Set<String> = localizedFieldsData.values.map { it.name }.toSet()

    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element.filterKeys { key -> key !in localizedField && key.substringBefore("#") !in localizedField })
            localizedFieldsData.values.forEach { localizedField ->
                val default = element[localizedField.name]
                val translations = element
                    .filterKeys { key -> key.contains('#') && key.substringBefore('#') == localizedField.name }
                    .map { (key, value) ->
                        key.substringAfter('#') to value
                    }.takeIf { it.isNotEmpty() }?.toMap()

                if (localizedField.isNullable && (default == null || default is JsonNull) && translations == null)
                    put(localizedField.name, JsonNull)
                else put(localizedField.name, buildJsonObject {
                    default?.let { put("default", it) }
                    translations?.let { put("translations", JsonObject(it)) }
                })
            }
        })
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return JsonObject(buildMap {
            putAll(element.filterKeys { it !in localizedField })
            element.filterKeys { key -> key in localizedField }.forEach { (key, value) ->
                if (value is JsonNull || value !is JsonObject) {
                    put(key, JsonNull)
                    return@forEach
                }
                val default = value["default"]
                val translations = value["translations"]
                if (localizedFieldsData[key]?.isNullable == true && (default == null || default is JsonNull) && (translations == null || translations is JsonNull)) {
                    put(key, JsonNull)
                } else {
                    if (default != null) put(key, default)
                    if (translations != null && translations !is JsonNull) {
                        require(translations is JsonObject) { "translations must be a JsonObject but was ${translations::class.simpleName}" }
                        translations.forEach { (language, value) ->
                            put("$key#$language", value)
                        }
                    }
                }
            }
        })
    }
}