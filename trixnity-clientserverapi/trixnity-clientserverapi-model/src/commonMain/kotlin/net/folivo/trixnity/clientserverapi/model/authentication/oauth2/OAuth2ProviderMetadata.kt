package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.MSC4191

@Serializable
data class OAuth2ProviderMetadata @MSC4191 constructor(
    val issuer: Url,
    @SerialName("authorization_endpoint") val authorizationEndpoint: Url,
    @SerialName("registration_endpoint") val registrationEndpoint: Url,
    @SerialName("revocation_endpoint") val revocationEndpoint: Url,
    @SerialName("token_endpoint") val tokenEndpoint: Url,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<CodeChallengeMethod>,
    @SerialName("response_types_supported") val responseTypesSupported: List<ResponseType>,
    @SerialName("response_modes_supported") val responseModesSupported: List<ResponseMode>,
    @SerialName("prompt_values_supported") val promptValuesSupported: List<PromptValue>,
    @SerialName("grant_types_supported") val grantTypesSupported: List<GrantType>,
    @SerialName("jwks_uri") val jwkSetUrl: Url? = null,

    // MSC4191
    @SerialName("account_management_actions_supported")
    val accountManagementActionsSupported: List<OAuth2AccountManagementAction>?,
    @SerialName("account_management_uri") val accountManagementUri: Url?
) {
    @OptIn(MSC4191::class)
    constructor(
        issuer: Url,
        authorizationEndpoint: Url,
        registrationEndpoint: Url,
        revocationEndpoint: Url,
        tokenEndpoint: Url,
        codeChallengeMethodsSupported: List<CodeChallengeMethod>,
        responseTypesSupported: List<ResponseType>,
        responseModesSupported: List<ResponseMode>,
        promptValuesSupported: List<PromptValue>,
        grantTypesSupported: List<GrantType>,
        jwkSetUrl: Url? = null,
    ) : this(
        issuer,
        authorizationEndpoint,
        registrationEndpoint,
        revocationEndpoint,
        tokenEndpoint,
        codeChallengeMethodsSupported,
        responseTypesSupported,
        responseModesSupported,
        promptValuesSupported,
        grantTypesSupported,
        jwkSetUrl,
        accountManagementActionsSupported = null,
        accountManagementUri = null
    )
}
