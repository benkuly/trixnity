package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.keys.Key

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroomthird_party_invite">matrix spec</a>
 */
@Serializable
data class ThirdPartyInviteEventContent(
    @SerialName("display_name")
    val displayName: String,
    @SerialName("key_validity_url")
    val keyValidityUrl: String,
    @SerialName("public_key")
    val publicKey: Key.Ed25519Key,
    @SerialName("public_keys")
    val publicKeys: List<PublicKey>? = null,
) : StateEventContent {
    @Serializable
    data class PublicKey(
        @SerialName("key_validity_url")
        val keyValidityUrl: String? = null,
        @SerialName("public_key")
        val publicKey: Key.Ed25519Key
    )
}
