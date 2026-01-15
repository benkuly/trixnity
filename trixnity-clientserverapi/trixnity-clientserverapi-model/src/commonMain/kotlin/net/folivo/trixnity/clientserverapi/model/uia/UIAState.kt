package net.folivo.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class UIAState(
    @SerialName("completed") val completed: List<AuthenticationType> = listOf(),
    @SerialName("flows") val flows: Set<FlowInformation> = setOf(),
    @SerialName("params") val parameter: @Serializable(with = ParameterMapSerializer::class) Map<AuthenticationType, Parameter>? = null,
    @SerialName("session") val session: String? = null
) {
    @Serializable
    data class FlowInformation(
        @SerialName("stages") val stages: List<AuthenticationType>
    )

    sealed interface Parameter {

        @Serializable
        data class TermsOfService(
            @SerialName("policies")
            val policies: Map<String, PolicyDefinition>
        ) : Parameter {

            @Serializable(with = PolicyDefinition.Serializer::class)
            data class PolicyDefinition(
                val version: String,
                val translations: Map<String, PolicyTranslation>,
            ) {
                @Serializable
                data class PolicyTranslation(
                    @SerialName("name") val name: String,
                    @SerialName("url") val url: String,
                )

                object Serializer : KSerializer<PolicyDefinition> {
                    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PolicyDefinition")
                    override fun deserialize(decoder: Decoder): PolicyDefinition {
                        require(decoder is JsonDecoder)
                        val jsonObject = decoder.decodeJsonElement() as? JsonObject
                            ?: throw SerializationException("expected JSON map")
                        val version =
                            jsonObject["version"] as? JsonPrimitive
                                ?: throw SerializationException("version should be a string")
                        return PolicyDefinition(
                            version.content, (jsonObject - "version").mapValues {
                                decoder.json.decodeFromJsonElement<PolicyTranslation>(it.value)
                            }
                        )
                    }

                    override fun serialize(encoder: Encoder, value: PolicyDefinition) {
                        require(encoder is JsonEncoder)
                        encoder.encodeJsonElement(buildJsonObject {
                            put("version", value.version)
                            value.translations.forEach { (key, value) ->
                                put(key, encoder.json.encodeToJsonElement<PolicyTranslation>(value))
                            }
                        })
                    }
                }
            }
        }

        @Serializable
        data class OAuth2(
            @SerialName("url") val url: String
        ) : Parameter

        @Serializable
        data class Unknown(val raw: JsonElement) : Parameter
    }

    class ParameterMapSerializer : KSerializer<Map<AuthenticationType, Parameter>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UIAStateParameterMap")
        override fun deserialize(decoder: Decoder): Map<AuthenticationType, Parameter> {
            require(decoder is JsonDecoder)
            val jsonObject =
                decoder.decodeJsonElement() as? JsonObject ?: throw SerializationException("expected JSON map")
            return jsonObject.mapKeys {
                decoder.json.decodeFromJsonElement<AuthenticationType>(JsonPrimitive(it.key))
            }.mapValues { (key, value) ->
                when (key) {
                    AuthenticationType.TermsOfService ->
                        decoder.json.decodeFromJsonElement<Parameter.TermsOfService>(value)

                    AuthenticationType.OAuth2 -> decoder.json.decodeFromJsonElement<Parameter.OAuth2>(value)

                    else -> Parameter.Unknown(value)
                }
            }
        }

        override fun serialize(encoder: Encoder, value: Map<AuthenticationType, Parameter>) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                buildJsonObject {
                    value.forEach { (key, value) ->
                        put(
                            key.name, when (value) {
                                is Parameter.TermsOfService -> encoder.json.encodeToJsonElement(value)
                                is Parameter.OAuth2 -> encoder.json.encodeToJsonElement(value)
                                is Parameter.Unknown -> value.raw
                            }
                        )
                    }
                }
            )
        }
    }
}