package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/pushers/set")
@HttpMethod(POST)
data class SetPushers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetPushers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("app_display_name")
        val appDisplayName: String,
        @SerialName("app_id")
        val appId: String,
        @SerialName("append")
        val append: Boolean? = null,
        @SerialName("data")
        val data: PusherData,
        @SerialName("device_display_name")
        val deviceDisplayName: String,
        @SerialName("kind")
        val kind: String? = null,
        @SerialName("lang")
        val lang: String,
        @SerialName("profile_tag")
        val profileTag: String? = null,
        @SerialName("pushkey")
        val pushkey: String,
    ) {
        @Serializable(with = PusherDataSerializer::class)
        data class PusherData(
            val format: String? = null,
            val url: String? = null,
            val customFields: JsonObject? = null
        )
    }
}

object PusherDataSerializer : KSerializer<SetPushers.Request.PusherData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PusherDataSerializer")

    override fun deserialize(decoder: Decoder): SetPushers.Request.PusherData {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("data must be a JsonObject")
        return SetPushers.Request.PusherData(
            format = jsonObject["format"]?.let { decoder.json.decodeFromJsonElement(it) },
            url = jsonObject["url"]?.let { decoder.json.decodeFromJsonElement(it) },
            customFields = JsonObject(buildMap {
                putAll(jsonObject)
                remove("format")
                remove("url")
            })
        )
    }

    override fun serialize(encoder: Encoder, value: SetPushers.Request.PusherData) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(buildMap {
            value.format?.let { put("format", JsonPrimitive(it)) }
            value.url?.let { put("url", JsonPrimitive(it)) }
            value.customFields?.let { putAll(it) }
        }))
    }

}