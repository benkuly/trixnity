package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroommember">matrix spec</a>
 */
@Serializable
data class MemberEventContent(
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("displayname")
    val displayName: String? = null,
    @SerialName("membership")
    val membership: Membership,
    @SerialName("is_direct")
    val isDirect: Boolean? = null,
    @SerialName("join_authorised_via_users_server")
    val joinAuthorisedViaUsersServer: UserId? = null,
    @SerialName("third_party_invite")
    val thirdPartyInvite: Invite? = null,
    @SerialName("reason")
    val reason: String? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    data class Invite(
        @SerialName("display_name")
        val displayName: String,
        @SerialName("signed")
        val signed: Signed<UserInfo, String>
    ) {
        @Serializable
        data class UserInfo(
            @SerialName("mxid")
            val userId: UserId,
            @SerialName("token")
            val token: String
        )
    }
}
