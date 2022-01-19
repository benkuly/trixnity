package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroommember">matrix spec</a>
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
    @SerialName("third_party_invite")
    val thirdPartyInvite: Invite? = null
) : StateEventContent {
    @Serializable
    enum class Membership {
        @SerialName("invite")
        INVITE,

        @SerialName("join")
        JOIN,

        @SerialName("knock")
        KNOCK,

        @SerialName("leave")
        LEAVE,

        @SerialName("ban")
        BAN
    }

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
            val mxid: UserId,
            @SerialName("token")
            val token: String
        )
    }
}
