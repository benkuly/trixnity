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
sealed interface LoginType {
    val name: String

    @Serializable
    object Password : LoginType {
        @SerialName("type")
        override val name = "m.login.password"
    }

    @Serializable
    data class Token(
        @SerialName("get_login_token") val getLoginToken: Boolean? = null,
    ) : LoginType {
        @SerialName("type")
        override val name = "m.login.token"
    }

    @Serializable
    object AppService : LoginType {
        @SerialName("type")
        override val name = "m.login.application_service"
    }

    @Serializable
    data class SSO(
        @SerialName("identity_providers")
        val identityProviders: Set<IdentityProvider> = setOf(),
    ) : LoginType {
        @SerialName("type")
        override val name = "m.login.sso"

        @Serializable
        data class IdentityProvider(
            @SerialName("brand")
            val brand: String? = null,
            @SerialName("icon")
            val icon: String? = null,
            @SerialName("id")
            val id: String,
            @SerialName("name")
            val name: String,
        )
    }

    data class Unknown(
        override val name: String,
        val raw: JsonObject
    ) : LoginType
}

object LoginTypeSerializer : KSerializer<LoginType> {
    override fun deserialize(decoder: Decoder): LoginType {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content
        requireNotNull(type)
        return when (type) {
            LoginType.Password.name -> decoder.json.decodeFromJsonElement<LoginType.Password>(jsonObj)
            "m.login.token" -> decoder.json.decodeFromJsonElement<LoginType.Token>(jsonObj)
            LoginType.AppService.name -> decoder.json.decodeFromJsonElement<LoginType.AppService>(jsonObj)
            "m.login.sso" -> decoder.json.decodeFromJsonElement<LoginType.SSO>(jsonObj)
            else -> LoginType.Unknown(type, jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: LoginType) {
        require(encoder is JsonEncoder)
        val jsonObject: JsonObject = when (value) {
            is LoginType.Password -> encoder.json.encodeToJsonElement(value).jsonObject
            is LoginType.Token -> encoder.json.encodeToJsonElement(value).jsonObject
            is LoginType.AppService -> encoder.json.encodeToJsonElement(value).jsonObject
            is LoginType.SSO -> encoder.json.encodeToJsonElement(value).jsonObject
            is LoginType.Unknown -> JsonObject(buildMap {
                putAll(value.raw)
            })
        }
        encoder.encodeJsonElement(JsonObject(buildMap {
            putAll(jsonObject)
            put("type", JsonPrimitive(value.name))
        }))
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LoginType")
}