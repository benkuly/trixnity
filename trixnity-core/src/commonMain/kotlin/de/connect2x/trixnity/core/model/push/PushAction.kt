package de.connect2x.trixnity.core.model.push

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#actions">matrix spec</a>
 */
@Serializable(with = PushAction.Serializer::class)
sealed interface PushAction {
    val name: String?

    data object Notify : PushAction {
        override val name = "notify"
    }

    data class SetSoundTweak(
        val value: String? = null
    ) : PushAction {
        override val name = "sound"
    }

    data class SetHighlightTweak(
        val value: Boolean = true
    ) : PushAction {
        override val name = "highlight"
    }

    data class Unknown(override val name: String?, val raw: JsonElement) : PushAction

    object Serializer : KSerializer<PushAction> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushAction")

        override fun deserialize(decoder: Decoder): PushAction {
            require(decoder is JsonDecoder)
            return when (val json = decoder.decodeJsonElement()) {
                is JsonPrimitive -> when (val name = json.content) {
                    Notify.name -> Notify
                    else -> Unknown(name, json)
                }

                is JsonObject -> when (val name = (json["set_tweak"] as? JsonPrimitive)?.contentOrNull) {
                    "sound" -> SetSoundTweak((json["value"] as? JsonPrimitive)?.contentOrNull)
                    "highlight" -> SetHighlightTweak(
                        json["value"]?.let { decoder.json.decodeFromJsonElement(it) } ?: true
                    )

                    else -> Unknown(name, json)
                }

                else -> Unknown(null, json)
            }
        }

        override fun serialize(encoder: Encoder, value: PushAction) {
            require(encoder is JsonEncoder)
            val json = when (value) {
                is Notify -> JsonPrimitive(Notify.name)
                is SetSoundTweak -> JsonObject(
                    buildMap {
                        put("set_tweak", JsonPrimitive(value.name))
                        value.value?.let { put("value", JsonPrimitive(it)) }
                    }
                )

                is SetHighlightTweak -> JsonObject(
                    buildMap {
                        put("set_tweak", JsonPrimitive(value.name))
                        put("value", JsonPrimitive(value.value))
                    }
                )

                is Unknown -> value.raw
            }
            encoder.encodeJsonElement(json)
        }
    }
}
