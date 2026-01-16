package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomthird_party_invite">matrix spec</a>
 */
@Serializable
data class ThirdPartyInviteEventContent(
    @SerialName("display_name")
    val displayName: String,
    @SerialName("key_validity_url")
    val keyValidityUrl: String,
    @SerialName("public_key")
    val publicKey: Ed25519KeyValue,
    @SerialName("public_keys")
    val publicKeys: List<PublicKey>? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class PublicKey(
        @SerialName("key_validity_url")
        val keyValidityUrl: String? = null,
        @SerialName("public_key")
        val publicKey: Ed25519KeyValue
    )
}
