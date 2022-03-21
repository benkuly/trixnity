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
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest.*

@Serializable(with = AuthenticationRequestSerializer::class)
sealed class AuthenticationRequest {
    abstract val type: AuthenticationType?

    @Serializable
    data class Password(
        @SerialName("identifier") val identifier: IdentifierType,
        @SerialName("password") val password: String,
    ) : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.Password
    }

    @Serializable
    data class Recaptcha(
        @SerialName("response") val response: JsonElement,
    ) : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.Recaptcha
    }

    @Serializable
    data class EmailIdentify(
        @SerialName("threepid_creds") val threePidCredentials: ThreePidCredentials,
    ) : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.EmailIdentity
    }

    @Serializable
    data class Msisdn(
        @SerialName("threepid_creds") val threePidCredentials: ThreePidCredentials,
    ) : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.Msisdn
    }

    @Serializable
    object Dummy : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.Dummy
    }

    @Serializable
    data class RegistrationToken(
        @SerialName("token") val token: String,
    ) : AuthenticationRequest() {
        @SerialName("type")
        override val type = AuthenticationType.RegistrationToken
    }

    @Serializable
    object Fallback : AuthenticationRequest() {
        @SerialName("type")
        override val type: AuthenticationType? = null
    }
}

object AuthenticationRequestSerializer : KSerializer<AuthenticationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthenticationRequestSerializer")

    override fun deserialize(decoder: Decoder): AuthenticationRequest {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("auth should be a json object")
        val type =
            jsonObject["type"]?.let { if (it !is JsonPrimitive) throw SerializationException("type should be a string") else it }?.content
        return when (type) {
            AuthenticationType.Password.name -> decoder.json.decodeFromJsonElement<Password>(jsonObject)
            AuthenticationType.Recaptcha.name -> decoder.json.decodeFromJsonElement<Recaptcha>(jsonObject)
            AuthenticationType.EmailIdentity.name -> decoder.json.decodeFromJsonElement<EmailIdentify>(jsonObject)
            AuthenticationType.Msisdn.name -> decoder.json.decodeFromJsonElement<Msisdn>(jsonObject)
            AuthenticationType.Dummy.name -> decoder.json.decodeFromJsonElement<Dummy>(jsonObject)
            AuthenticationType.RegistrationToken.name ->
                decoder.json.decodeFromJsonElement<RegistrationToken>(jsonObject)
            null -> decoder.json.decodeFromJsonElement<Fallback>(jsonObject)
            else -> throw SerializationException("could not deserialize authentication request")
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
            is Fallback -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(JsonObject(buildMap {
            value.type?.name?.let { put("type", JsonPrimitive(it)) }
            putAll(jsonElement.jsonObject)
        }))
    }
}