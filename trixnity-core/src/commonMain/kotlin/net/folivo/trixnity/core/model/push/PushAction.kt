package net.folivo.trixnity.core.model.push

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
@Serializable(with = PushActionSerializer::class)
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
}

object PushActionSerializer : KSerializer<PushAction> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PushActionSerializer")

    override fun deserialize(decoder: Decoder): PushAction {
        require(decoder is JsonDecoder)
        return when (val json = decoder.decodeJsonElement()) {
            is JsonPrimitive -> when (val name = json.content) {
                PushAction.Notify.name -> PushAction.Notify
                else -> PushAction.Unknown(name, json)
            }

            is JsonObject -> when (val name = (json["set_tweak"] as? JsonPrimitive)?.content) {
                "sound" -> PushAction.SetSoundTweak((json["value"] as? JsonPrimitive)?.content)
                "highlight" -> PushAction.SetHighlightTweak(
                    json["value"]?.let { decoder.json.decodeFromJsonElement(it) } ?: true
                )

                else -> PushAction.Unknown(name, json)
            }

            else -> PushAction.Unknown(null, json)
        }
    }

    override fun serialize(encoder: Encoder, value: PushAction) {
        require(encoder is JsonEncoder)
        val json = when (value) {
            is PushAction.Notify -> JsonPrimitive(PushAction.Notify.name)
            is PushAction.SetSoundTweak -> JsonObject(
                buildMap {
                    put("set_tweak", JsonPrimitive(value.name))
                    value.value?.let { put("value", JsonPrimitive(it)) }
                }
            )

            is PushAction.SetHighlightTweak -> JsonObject(
                buildMap {
                    put("set_tweak", JsonPrimitive(value.name))
                    put("value", JsonPrimitive(value.value))
                }
            )

            is PushAction.Unknown -> value.raw
        }
        encoder.encodeJsonElement(json)
    }

}
