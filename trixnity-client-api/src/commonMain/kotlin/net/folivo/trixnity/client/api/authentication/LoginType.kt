package net.folivo.trixnity.client.api.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = LoginTypeSerializer::class)
sealed class LoginType {
    abstract val name: String

    object Password : LoginType() {
        @SerialName("type")
        override val name: String
            get() = "m.login.password"
    }

    object Token : LoginType() {
        @SerialName("type")
        override val name: String
            get() = "m.login.token"
    }

    data class Unknown(
        @SerialName("type")
        override val name: String
    ) : LoginType()
}

object LoginTypeSerializer : KSerializer<LoginType> {
    override fun deserialize(decoder: Decoder): LoginType {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        return when (type) {
            LoginType.Password.name -> LoginType.Password
            LoginType.Token.name -> LoginType.Token
            else -> LoginType.Unknown(type)
        }
    }

    override fun serialize(encoder: Encoder, value: LoginType) {
        throw SerializationException("should never be serialized")
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LoginType")
}