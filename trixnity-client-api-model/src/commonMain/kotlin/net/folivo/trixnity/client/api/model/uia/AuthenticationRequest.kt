package net.folivo.trixnity.client.api.model.uia

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

sealed interface AuthenticationRequest {

    @Serializable
    data class Password(
        @SerialName("identifier") val identifier: IdentifierType,
        @SerialName("password") val password: String,
    ) : AuthenticationRequest

    @Serializable
    data class Recaptcha(
        @SerialName("response") val response: JsonElement,
    ) : AuthenticationRequest

    @Serializable
    data class EmailIdentify(
        @SerialName("threepid_creds") val threePidCredentials: ThreePidCredentials,
    ) : AuthenticationRequest

    @Serializable
    data class Msisdn(
        @SerialName("threepid_creds") val threePidCredentials: ThreePidCredentials,
    ) : AuthenticationRequest

    @Serializable
    object Dummy : AuthenticationRequest

    @Serializable
    data class RegistrationToken(
        @SerialName("token") val token: String,
    ) : AuthenticationRequest

    @Serializable
    object Fallback : AuthenticationRequest
}

class AuthenticationRequestSerializer(val session: String?) : KSerializer<AuthenticationRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AuthenticationRequestSerializer+$session")

    override fun deserialize(decoder: Decoder): AuthenticationRequest {
        throw SerializationException("deserialize of ${AuthenticationRequest::class} not supported")
    }

    override fun serialize(encoder: Encoder, value: AuthenticationRequest) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is AuthenticationRequest.Password -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.Password.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.Password.name
                ), value
            )
            is AuthenticationRequest.Recaptcha -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.Recaptcha.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.Recaptcha.name
                ), value
            )
            is AuthenticationRequest.EmailIdentify -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.EmailIdentify.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.EmailIdentity.name
                ), value
            )
            is AuthenticationRequest.Msisdn -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.Msisdn.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.Msisdn.name
                ), value
            )
            is AuthenticationRequest.Dummy -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.Dummy.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.Dummy.name
                ), value
            )
            is AuthenticationRequest.RegistrationToken -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.RegistrationToken.serializer(),
                    "session" to session,
                    "type" to AuthenticationType.RegistrationToken.name
                ), value
            )
            is AuthenticationRequest.Fallback -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    AuthenticationRequest.Fallback.serializer(),
                    "session" to session,
                ), value
            )
        }
        encoder.encodeJsonElement(jsonElement)
    }

}