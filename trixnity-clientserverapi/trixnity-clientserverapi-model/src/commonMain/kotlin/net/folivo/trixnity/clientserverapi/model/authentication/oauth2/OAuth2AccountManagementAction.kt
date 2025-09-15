package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.MSC4191

@OptIn(MSC4191::class)
private class OAuth2AccountManagementActionSerializer : KSerializer<OAuth2AccountManagementAction> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("OAuth2AccountManagementAction", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): OAuth2AccountManagementAction =
        when (val value = decoder.decodeString().lowercase()) {
            "org.matrix.profile" -> OAuth2AccountManagementAction.ViewProfile
            "org.matrix.sessions_list" -> OAuth2AccountManagementAction.ListSessions
            "org.matrix.session_view" -> OAuth2AccountManagementAction.ViewSession
            "org.matrix.session_end" -> OAuth2AccountManagementAction.EndSession
            "org.matrix.deactivate_account" -> OAuth2AccountManagementAction.DeactivateAccount
            "org.matrix.cross_signing_reset" -> OAuth2AccountManagementAction.ResetCrossSigning
            else -> OAuth2AccountManagementAction.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: OAuth2AccountManagementAction) =
        encoder.encodeString(value.toString())
}

@MSC4191
@Serializable(with = OAuth2AccountManagementActionSerializer::class)
sealed interface OAuth2AccountManagementAction {
    object ViewProfile : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.profile"
    }

    object ListSessions : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.sessions_list"
    }

    object ViewSession : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.session_view"
    }

    object EndSession : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.session_end"
    }

    object DeactivateAccount : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.deactivate_account"
    }

    object ResetCrossSigning : OAuth2AccountManagementAction {
        override fun toString(): String = "org.matrix.cross_signing_reset"
    }

    data class Unknown(private val value: String) : OAuth2AccountManagementAction {
        override fun toString(): String = value
    }
}
