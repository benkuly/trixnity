package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEvent.UnsignedData
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.StrippedStateEvent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-member">matrix spec</a>
 */
@Serializable
data class MemberEvent(
    @SerialName("content") override val content: MemberEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("state_key") val relatedUser: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: MemberUnsignedData,
    @SerialName("prev_content") override val previousContent: MemberEventContent? = null,
    @SerialName("type") override val type: String = "m.room.member"
) : StateEvent<MemberEvent.MemberEventContent> {

    override val stateKey: String = relatedUser.full

    @Serializable
    data class MemberUnsignedData(
        @SerialName("age") override val age: Long? = null,
        @SerialName("redactedBecause") override val redactedBecause: Event<@Polymorphic Any>? = null,
        @SerialName("transaction_id") override val transactionId: String? = null,
        @SerialName("invite_room_state") val inviteRoomState: List<StrippedStateEvent>? = null
    ) : UnsignedData

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
    ) {
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

}