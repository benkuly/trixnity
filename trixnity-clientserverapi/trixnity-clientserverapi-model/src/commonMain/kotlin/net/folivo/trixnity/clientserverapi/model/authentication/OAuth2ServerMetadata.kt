package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.Url
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.MSC4191

@OptIn(MSC4191::class)
object OAuth2AccountManagementActionSerializer : KSerializer<OAuth2ServerMetadata.OAuth2AccountManagementAction> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("OAuth2AccountManagementAction", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): OAuth2ServerMetadata.OAuth2AccountManagementAction =
        when (val value = decoder.decodeString().lowercase()) {
            OAuth2ServerMetadata.OAuth2AccountManagementAction.ViewProfile.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.ViewProfile
            OAuth2ServerMetadata.OAuth2AccountManagementAction.ListSessions.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.ListSessions
            OAuth2ServerMetadata.OAuth2AccountManagementAction.ViewSession.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.ViewSession
            OAuth2ServerMetadata.OAuth2AccountManagementAction.EndSession.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.EndSession
            OAuth2ServerMetadata.OAuth2AccountManagementAction.DeactivateAccount.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.DeactivateAccount
            OAuth2ServerMetadata.OAuth2AccountManagementAction.ResetCrossSigning.value -> OAuth2ServerMetadata.OAuth2AccountManagementAction.ResetCrossSigning
            else -> OAuth2ServerMetadata.OAuth2AccountManagementAction.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: OAuth2ServerMetadata.OAuth2AccountManagementAction) =
        encoder.encodeString(value.value)
}

object PromptValueSerializer : KSerializer<OAuth2ServerMetadata.PromptValue> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("PromptValue", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OAuth2ServerMetadata.PromptValue = when (val value = decoder.decodeString().lowercase()) {
        OAuth2ServerMetadata.PromptValue.None.value -> OAuth2ServerMetadata.PromptValue.None
        OAuth2ServerMetadata.PromptValue.Login.value -> OAuth2ServerMetadata.PromptValue.Login
        OAuth2ServerMetadata.PromptValue.Consent.value -> OAuth2ServerMetadata.PromptValue.Consent
        OAuth2ServerMetadata.PromptValue.SelectAccount.value -> OAuth2ServerMetadata.PromptValue.SelectAccount
        else -> OAuth2ServerMetadata.PromptValue.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: OAuth2ServerMetadata.PromptValue) = encoder.encodeString(value.value)
}

object ResponseModeSerializer : KSerializer<OAuth2ServerMetadata.ResponseMode> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseMode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OAuth2ServerMetadata.ResponseMode = when (val value = decoder.decodeString().lowercase()) {
        OAuth2ServerMetadata.ResponseMode.Fragment.value -> OAuth2ServerMetadata.ResponseMode.Fragment
        OAuth2ServerMetadata.ResponseMode.Query.value -> OAuth2ServerMetadata.ResponseMode.Query
        else -> OAuth2ServerMetadata.ResponseMode.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: OAuth2ServerMetadata.ResponseMode) = encoder.encodeString(value.value)
}

@Serializable
data class OAuth2ServerMetadata(
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
    @SerialName("account_management_actions_supported")
    @MSC4191 @OptIn(MSC4191::class) val accountManagementActionsSupported: List<OAuth2AccountManagementAction>? = null,
    @MSC4191 @SerialName("account_management_uri") val accountManagementUri: Url? = null
) {
    @Serializable(with = PromptValueSerializer::class)
    sealed interface PromptValue {
        val value: String

        object None : PromptValue {
            override val value: String = "none"
        }

        object Login : PromptValue {
            override val value: String = "login"
        }

        object Consent : PromptValue {
            override val value: String = "consent"
        }

        object SelectAccount : PromptValue {
            override val value: String = "select_account"
        }

        data class Unknown(override val value: String) : PromptValue
    }

    @Serializable(with = ResponseModeSerializer::class)
    sealed interface ResponseMode {
        val value: String

        object Query : ResponseMode {
            override val value: String = "query"
        }

        object Fragment : ResponseMode {
            override val value: String = "fragment"
        }

        data class Unknown(override val value: String) : ResponseMode
    }

    @MSC4191
    @Serializable(with = OAuth2AccountManagementActionSerializer::class)
    sealed interface OAuth2AccountManagementAction {
        val value: String

        object ViewProfile : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.profile"
        }

        object ListSessions : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.sessions_list"
        }

        object ViewSession : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.session_view"
        }

        object EndSession : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.session_end"
        }

        object DeactivateAccount : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.deactivate_account"
        }

        object ResetCrossSigning : OAuth2AccountManagementAction {
            override val value: String = "org.matrix.cross_signing_reset"
        }

        data class Unknown(override val value: String) : OAuth2AccountManagementAction
    }
}
