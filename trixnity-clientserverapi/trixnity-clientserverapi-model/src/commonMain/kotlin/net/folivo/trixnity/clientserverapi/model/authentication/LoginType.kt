package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = LoginType.Serializer::class)
sealed interface LoginType {
    val name: String

    @Serializable
    data object Password : LoginType {
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
    data object AppService : LoginType {
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

    object Serializer : KSerializer<LoginType> {
        override fun deserialize(decoder: Decoder): LoginType {
            require(decoder is JsonDecoder)
            val jsonObj = decoder.decodeJsonElement().jsonObject
            val type = jsonObj["type"]?.jsonPrimitive?.content
            requireNotNull(type)
            return when (type) {
                Password.name -> decoder.json.decodeFromJsonElement<Password>(jsonObj)
                "m.login.token" -> decoder.json.decodeFromJsonElement<Token>(jsonObj)
                AppService.name -> decoder.json.decodeFromJsonElement<AppService>(jsonObj)
                "m.login.sso" -> decoder.json.decodeFromJsonElement<SSO>(jsonObj)
                else -> Unknown(type, jsonObj)
            }
        }

        override fun serialize(encoder: Encoder, value: LoginType) {
            require(encoder is JsonEncoder)
            val jsonObject: JsonObject = when (value) {
                is Password -> encoder.json.encodeToJsonElement(value).jsonObject
                is Token -> encoder.json.encodeToJsonElement(value).jsonObject
                is AppService -> encoder.json.encodeToJsonElement(value).jsonObject
                is SSO -> encoder.json.encodeToJsonElement(value).jsonObject
                is Unknown -> JsonObject(buildMap {
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
}