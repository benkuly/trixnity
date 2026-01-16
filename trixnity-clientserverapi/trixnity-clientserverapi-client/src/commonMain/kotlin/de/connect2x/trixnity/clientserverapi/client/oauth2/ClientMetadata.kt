package de.connect2x.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.GrantType
import de.connect2x.trixnity.clientserverapi.model.authentication.oauth2.ResponseType

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = ClientMetadata.Serializer::class)
@KeepGeneratedSerializer
internal data class ClientMetadata(
    @SerialName("application_type") val applicationType: ApplicationType = ApplicationType.Web,
    @SerialName("client_name") val clientName: LocalizedField<String>? = null,
    @SerialName("client_uri") val clientUri: String,
    @SerialName("grant_types") val grantTypes: Set<GrantType>? = null,
    @SerialName("logo_uri") val logoUri: LocalizedField<String>? = null,
    @SerialName("policy_uri") val policyUri: LocalizedField<String>? = null,
    @SerialName("redirect_uris") val redirectUris: Set<String>? = null,
    @SerialName("response_types") val responseTypes: Set<ResponseType>? = null,
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: TokenEndpointAuthMethod? = null,
    @SerialName("tos_uri") val tosUri: LocalizedField<String>? = null,
) {
    object Serializer : LocalizedObjectSerializer<ClientMetadata>(ClientMetadata.generatedSerializer())
}