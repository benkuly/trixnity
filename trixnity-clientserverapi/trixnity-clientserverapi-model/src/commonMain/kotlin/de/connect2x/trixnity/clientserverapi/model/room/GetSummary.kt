package de.connect2x.trixnity.clientserverapi.model.room

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.JoinRulesEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#get_matrixclientv1room_summaryroomidoralias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/room_summary/{roomIdOrRoomAliasId}")
@HttpMethod(GET)
data class GetSummary(
    @SerialName("roomIdOrRoomAliasId") val roomIdOrRoomAliasId: String,
    @SerialName("via") val via: Set<String>? = null,
) : MatrixEndpoint<Unit, GetSummary.Response> {
    @Serializable
    data class Response(
        @SerialName("allowed_room_ids") val allowedRoomIds: Set<RoomId>? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("canonical_alias") val canonicalAlias: RoomAliasId? = null,
        @SerialName("encryption") val encryption: EncryptionAlgorithm? = null,
        @SerialName("guest_can_join") val guestCanJoin: Boolean,
        @SerialName("join_rule") val joinRule: JoinRulesEventContent.JoinRule = JoinRulesEventContent.JoinRule.Public,
        @SerialName("membership") val membership: Membership? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("num_joined_members") val joinedMembersCount: Long,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("room_type") val roomType: CreateEventContent.RoomType? = null,
        @SerialName("room_version") val roomVersion: String? = null,
        @SerialName("topic") val topic: String? = null,
        @SerialName("world_readable") val worldReadable: Boolean,
    )
}