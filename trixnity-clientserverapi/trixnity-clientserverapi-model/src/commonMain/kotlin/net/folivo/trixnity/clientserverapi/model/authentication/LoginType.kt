package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = LoginTypeSerializer::class)
sealed class LoginType {
    abstract val name: String

    @Serializable
    object Password : LoginType() {
        @SerialName("type")
        override val name = "m.login.password"
    }

    @Serializable
    object Token : LoginType() {
        @SerialName("type")
        override val name = "m.login.token"
    }

    @Serializable
    object AppService : LoginType() {
        @SerialName("type")
        override val name = "m.login.appservice"
    }

    data class Unknown(
        override val name: String,
        val raw: JsonObject
    ) : LoginType()
}

object LoginTypeSerializer : KSerializer<LoginType> {
    override fun deserialize(decoder: Decoder): LoginType {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        return when (type) {
            "m.login.password" -> decoder.json.decodeFromJsonElement<LoginType.Password>(jsonObj)
            "m.login.token" -> decoder.json.decodeFromJsonElement<LoginType.Token>(jsonObj)
            "m.login.appservice" -> decoder.json.decodeFromJsonElement<LoginType.AppService>(jsonObj)
            else -> LoginType.Unknown(type, jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: LoginType) {
        require(encoder is JsonEncoder)
        val jsonObject = when (value) {
            is LoginType.Password -> JsonObject(mapOf("type" to JsonPrimitive("m.login.password")))
            is LoginType.Token -> JsonObject(mapOf("type" to JsonPrimitive("m.login.token")))
            is LoginType.AppService -> JsonObject(mapOf("type" to JsonPrimitive("m.login.appservice")))
            is LoginType.Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonObject)
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LoginType")
}