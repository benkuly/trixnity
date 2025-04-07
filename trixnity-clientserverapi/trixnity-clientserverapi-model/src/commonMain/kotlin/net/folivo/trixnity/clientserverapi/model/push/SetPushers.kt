package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3pushersset">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushers/set")
@HttpMethod(POST)
data class SetPushers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetPushers.Request, Unit> {
    @Serializable(with = SetPushersRequestSerializer::class)
    sealed interface Request {
        val appId: String
        val pushkey: String
        val kind: String?

        @Serializable
        data class Set(
            @SerialName("app_id")
            override val appId: String,
            @SerialName("pushkey")
            override val pushkey: String,
            @SerialName("kind")
            override val kind: String,
            @SerialName("app_display_name")
            val appDisplayName: String,
            @SerialName("device_display_name")
            val deviceDisplayName: String,
            @SerialName("lang")
            val lang: String,
            @SerialName("data")
            val data: PusherData,
            @SerialName("append")
            val append: Boolean? = null,
            @SerialName("profile_tag")
            val profileTag: String? = null,
        ) : Request

        @Serializable
        data class Remove(
            @SerialName("app_id")
            override val appId: String,
            @SerialName("pushkey")
            override val pushkey: String,
        ) : Request {
            @SerialName("kind")
            override val kind: String? = null
        }
    }
}

object SetPushersRequestSerializer : KSerializer<SetPushers.Request> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SetPushersRequest")

    override fun deserialize(decoder: Decoder): SetPushers.Request {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val kind = jsonObject["kind"]
        val isRemove = kind == null || kind is JsonNull
        return when (isRemove) {
            true -> decoder.json.decodeFromJsonElement<SetPushers.Request.Remove>(jsonObject)
            false -> decoder.json.decodeFromJsonElement<SetPushers.Request.Set>(jsonObject)
        }
    }

    override fun serialize(encoder: Encoder, value: SetPushers.Request) {
        require(encoder is JsonEncoder)
        when (value) {
            is SetPushers.Request.Remove -> {
                encoder.encodeJsonElement(
                    JsonObject(buildMap {
                        putAll(encoder.json.encodeToJsonElement<SetPushers.Request.Remove>(value).jsonObject)
                        put("kind", JsonNull)
                    })
                )
            }

            is SetPushers.Request.Set ->
                encoder.encodeSerializableValue(SetPushers.Request.Set.serializer(), value)
        }
    }
}