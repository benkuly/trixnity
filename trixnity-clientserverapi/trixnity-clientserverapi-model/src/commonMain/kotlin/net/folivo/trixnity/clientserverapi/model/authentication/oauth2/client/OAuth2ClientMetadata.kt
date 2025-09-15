package net.folivo.trixnity.clientserverapi.model.authentication.oauth2.client

import io.ktor.http.Url
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.CodeChallengeMethod
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ResponseType
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.TokenEndpointAuthMethod
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

private class OAuth2ClientMetadataSerializer : KSerializer<OAuth2ClientMetadata> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OAuth2ClientMetadata") {
        element<ApplicationType>("application_type")
        element<Url>("client_uri")
        element<List<Url>>("redirect_uris")
        element<List<GrantType>>("grant_types")
        element<List<ResponseType>>("response_types")
        element<TokenEndpointAuthMethod>("token_endpoint_auth_method")
        element<LocalizedField<String>?>("client_name", isOptional = true)
        element<LocalizedField<Url>?>("policy_uri", isOptional = true)
        element<LocalizedField<Url>?>("logo_uri", isOptional = true)
        element<LocalizedField<Url>?>("tos_uri", isOptional = true)
        element<String?>("client_id", isOptional = true)
        element<CodeChallengeMethod?>("id_token_signed_response_alg", isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: OAuth2ClientMetadata) {
        require(encoder is JsonEncoder) { "This serializer works only with JSON" }
        val json = encoder.json

        encoder.encodeJsonElement(buildJsonObject {
            put("application_type", json.encodeToJsonElement(value.applicationType))
            put("client_uri", json.encodeToJsonElement(value.clientUri))
            put("redirect_uris", json.encodeToJsonElement(value.redirectUris))
            put("grant_types", json.encodeToJsonElement(value.grantTypes))
            put("response_types", json.encodeToJsonElement(value.responseTypes))
            put("token_endpoint_auth_method", json.encodeToJsonElement(value.tokenEndpointAuthMethod))
            put("id_token_signed_response_alg", json.encodeToJsonElement(value.idTokenSigningAlgorithm))
            put("client_id", json.encodeToJsonElement(value.clientId))

            value.clientName?.forEach { language, value ->
                put(if (language == null) "client_name" else "client_name#$language", json.encodeToJsonElement(value))
            }

            value.policyUri?.forEach { language, value ->
                put(if (language == null) "policy_uri" else "policy_uri#$language", json.encodeToJsonElement(value))
            }

            value.logoUri?.forEach { language, value ->
                put(if (language == null) "logo_uri" else "logo_uri#$language", json.encodeToJsonElement(value))
            }

            value.tosUri?.forEach { language, value ->
                put(if (language == null) "tos_uri" else "tos_uri#$language", json.encodeToJsonElement(value))
            }
        })
    }

    override fun deserialize(decoder: Decoder): OAuth2ClientMetadata {
        require(decoder is JsonDecoder) { "This serializer works only with JSON" }
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val json = decoder.json

        fun <T> extractLocalizedField(prefix: String, deserializer: KSerializer<T>): LocalizedField<T>? {
            val translations = mutableMapOf<String, T>()
            var default: T? = null
            jsonObject.forEach { (key, element) ->
                if (key == prefix) {
                    default = json.decodeFromJsonElement(deserializer, element)
                    return@forEach
                }

                if (!key.startsWith("$prefix#"))
                    return@forEach

                translations[key.removePrefix("$prefix#")] = json.decodeFromJsonElement(deserializer, element)
            }

            return LocalizedField(
                default ?: return null,
                translations
            )
        }

        return OAuth2ClientMetadata(
            applicationType = json.decodeFromJsonElement(
                ApplicationType.serializer(),
                jsonObject.getValue("application_type")
            ),
            clientUri = json.decodeFromJsonElement(Url.serializer(), jsonObject.getValue("client_uri")),
            redirectUris = json.decodeFromJsonElement(
                ListSerializer(Url.serializer()),
                jsonObject.getValue("redirect_uris")
            ),
            grantTypes = json.decodeFromJsonElement(
                ListSerializer(GrantType.serializer()),
                jsonObject.getValue("grant_types")
            ),
            responseTypes = json.decodeFromJsonElement(
                ListSerializer(ResponseType.serializer()),
                jsonObject.getValue("response_types")
            ),
            tokenEndpointAuthMethod = json.decodeFromJsonElement(
                TokenEndpointAuthMethod.serializer(),
                jsonObject.getValue("token_endpoint_auth_method")
            ),
            clientName = extractLocalizedField("client_name", String.serializer()),
            policyUri = extractLocalizedField("policy_uri", Url.serializer()),
            logoUri = extractLocalizedField("logo_uri", Url.serializer()),
            tosUri = extractLocalizedField("tos_uri", Url.serializer()),
            clientId = jsonObject["client_id"]?.jsonPrimitive?.content,
            idTokenSigningAlgorithm = jsonObject["id_token_signed_response_alg"].let {
                if (!(it?.jsonPrimitive?.isString ?: false)) {
                    return@let null
                }

                json.decodeFromJsonElement<CodeChallengeMethod?>(CodeChallengeMethod.serializer(), it)
            }
        )
    }
}


@Serializable(with = OAuth2ClientMetadataSerializer::class)
data class OAuth2ClientMetadata(
    @SerialName("application_type") val applicationType: ApplicationType,
    @SerialName("client_uri") val clientUri: Url,
    @SerialName("redirect_uris") val redirectUris: List<Url>,
    @SerialName("grant_types") val grantTypes: List<GrantType>,
    @SerialName("response_types") val responseTypes: List<ResponseType>,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: TokenEndpointAuthMethod,
    @SerialName("client_name") val clientName: LocalizedField<String>? = null,
    @SerialName("policy_uri") val policyUri: LocalizedField<Url>? = null,
    @SerialName("logo_uri") val logoUri: LocalizedField<Url>? = null,
    @SerialName("tos_uri") val tosUri: LocalizedField<Url>? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("id_token_signed_response_alg") val idTokenSigningAlgorithm: CodeChallengeMethod? = null
)
