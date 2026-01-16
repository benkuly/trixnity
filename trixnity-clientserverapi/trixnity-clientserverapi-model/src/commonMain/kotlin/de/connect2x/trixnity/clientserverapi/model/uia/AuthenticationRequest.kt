package de.connect2x.trixnity.clientserverapi.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType

@Serializable(with = AuthenticationRequest.Serializer::class)
sealed interface AuthenticationRequest {
    val type: AuthenticationType?

    @Serializable
    data class Password(
        @SerialName("identifier") val identifier: IdentifierType,
        @SerialName("password") val password: String,
    ) : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.Password
    }

    @Serializable
    data class Recaptcha(
        @SerialName("response") val response: JsonElement,
    ) : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.Recaptcha
    }

    @Serializable
    data class EmailIdentify(
        @SerialName("threepid_creds") val thirdPidCredentials: ThirdPidCredentials,
    ) : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.EmailIdentity
    }

    @Serializable
    data class Msisdn(
        @SerialName("threepid_creds") val thirdPidCredentials: ThirdPidCredentials,
    ) : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.Msisdn
    }

    @Serializable
    data object Dummy : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.Dummy
    }

    @Serializable
    data class RegistrationToken(
        @SerialName("token") val token: String,
    ) : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.RegistrationToken
    }

    @Serializable
    data object OAuth2 : AuthenticationRequest {
        @SerialName("type")
        override val type = AuthenticationType.OAuth2
    }

    @Serializable
    data object Fallback : AuthenticationRequest {
        @SerialName("type")
        override val type: AuthenticationType? = null
    }
    
    data class Unknown(
        val raw: JsonObject,
        override val type: AuthenticationType.Unknown?,
    ) : AuthenticationRequest

    object Serializer : KSerializer<AuthenticationRequest> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthenticationRequest")

        override fun deserialize(decoder: Decoder): AuthenticationRequest {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement()
            if (jsonObject !is JsonObject) throw SerializationException("auth should be a json object")
            val type =
                jsonObject["type"]?.let {
                    it as? JsonPrimitive ?: throw SerializationException("type should be a string")
                }?.content
            return when (type) {
                AuthenticationType.Password.name -> decoder.json.decodeFromJsonElement<Password>(jsonObject)
                AuthenticationType.Recaptcha.name -> decoder.json.decodeFromJsonElement<Recaptcha>(jsonObject)
                AuthenticationType.EmailIdentity.name -> decoder.json.decodeFromJsonElement<EmailIdentify>(jsonObject)
                AuthenticationType.Msisdn.name -> decoder.json.decodeFromJsonElement<Msisdn>(jsonObject)
                AuthenticationType.Dummy.name -> decoder.json.decodeFromJsonElement<Dummy>(jsonObject)
                AuthenticationType.RegistrationToken.name ->
                    decoder.json.decodeFromJsonElement<RegistrationToken>(jsonObject)

                AuthenticationType.OAuth2.name -> decoder.json.decodeFromJsonElement<OAuth2>(jsonObject)

                null -> {
                    if (jsonObject.size == 1) decoder.json.decodeFromJsonElement<Fallback>(jsonObject)
                    else Unknown(jsonObject, null)
                }

                else -> Unknown(jsonObject, AuthenticationType.Unknown(type))
            }
        }

        override fun serialize(encoder: Encoder, value: AuthenticationRequest) {
            require(encoder is JsonEncoder)
            val jsonElement = when (value) {
                is Password -> encoder.json.encodeToJsonElement(value)
                is Recaptcha -> encoder.json.encodeToJsonElement(value)
                is EmailIdentify -> encoder.json.encodeToJsonElement(value)
                is Msisdn -> encoder.json.encodeToJsonElement(value)
                is Dummy -> encoder.json.encodeToJsonElement(value)
                is RegistrationToken -> encoder.json.encodeToJsonElement(value)
                is OAuth2 -> encoder.json.encodeToJsonElement(value)
                is Fallback -> encoder.json.encodeToJsonElement(value)
                is Unknown -> encoder.json.encodeToJsonElement(JsonObject(buildMap {
                    putAll(value.raw)
                    put("type", encoder.json.encodeToJsonElement(value.type))
                }))
            }
            encoder.encodeJsonElement(JsonObject(buildMap {
                value.type?.name?.let { put("type", JsonPrimitive(it)) }
                putAll(jsonElement.jsonObject)
            }))
        }
    }
}