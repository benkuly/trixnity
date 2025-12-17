package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.MSC4191

@Serializable
data class ServerMetadata(
    @SerialName("authorization_endpoint") val authorizationEndpoint: Url,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: Set<CodeChallengeMethod>,
    @SerialName("grant_types_supported") val grantTypesSupported: Set<GrantType>,
    @SerialName("issuer") val issuer: Url,
    @SerialName("prompt_values_supported") val promptValuesSupported: Set<PromptValue>? = null,
    @SerialName("registration_endpoint") val registrationEndpoint: Url,
    @SerialName("response_modes_supported") val responseModesSupported: Set<ResponseMode>,
    @SerialName("response_types_supported") val responseTypesSupported: Set<ResponseType>,
    @SerialName("revocation_endpoint") val revocationEndpoint: Url,
    @SerialName("token_endpoint") val tokenEndpoint: Url,

    @SerialName("account_management_actions_supported")
    @MSC4191 @OptIn(MSC4191::class) val accountManagementActionsSupported: Set<OAuth2AccountManagementAction>? = null,
    @MSC4191 @SerialName("account_management_uri") val accountManagementUri: Url? = null
)