package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-member">matrix spec</a>
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
        val signed: Signed
    ) {
        @Serializable
        data class Signed(
            @SerialName("mxid")
            val mxid: UserId,
            @SerialName("signatures")
            val signatures: JsonObject, // TODO signatures
            @SerialName("token")
            val token: String
        )
    }
}
