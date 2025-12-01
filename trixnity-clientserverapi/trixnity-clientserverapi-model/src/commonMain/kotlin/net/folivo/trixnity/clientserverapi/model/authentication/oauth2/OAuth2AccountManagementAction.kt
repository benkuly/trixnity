package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.MSC4191

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

@OptIn(MSC4191::class)
object OAuth2AccountManagementActionSerializer : KSerializer<OAuth2AccountManagementAction> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("OAuth2AccountManagementAction", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OAuth2AccountManagementAction =
        when (val value = decoder.decodeString().lowercase()) {
            OAuth2AccountManagementAction.ViewProfile.value -> OAuth2AccountManagementAction.ViewProfile
            OAuth2AccountManagementAction.ListSessions.value -> OAuth2AccountManagementAction.ListSessions
            OAuth2AccountManagementAction.ViewSession.value -> OAuth2AccountManagementAction.ViewSession
            OAuth2AccountManagementAction.EndSession.value -> OAuth2AccountManagementAction.EndSession
            OAuth2AccountManagementAction.DeactivateAccount.value -> OAuth2AccountManagementAction.DeactivateAccount
            OAuth2AccountManagementAction.ResetCrossSigning.value -> OAuth2AccountManagementAction.ResetCrossSigning
            else -> OAuth2AccountManagementAction.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: OAuth2AccountManagementAction) =
        encoder.encodeString(value.value)
}