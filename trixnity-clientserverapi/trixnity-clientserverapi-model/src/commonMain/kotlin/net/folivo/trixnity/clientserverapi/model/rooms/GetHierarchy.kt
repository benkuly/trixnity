package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#get_matrixclientv1roomsroomidhierarchy">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/hierarchy")
@HttpMethod(GET)
data class GetHierarchy(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("from") val from: String? = null,
    @SerialName("limit") val limit: Long? = null,
    @SerialName("max_depth") val maxDepth: Long? = null,
    @SerialName("suggested_only") val suggestedOnly: Boolean = false,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetHierarchy.Response> {
    @Serializable
    data class Response(
        @SerialName("next_batch") val nextBatch: String? = null,
        @SerialName("rooms") val rooms: List<PublicRoomsChunk>,
    ) {
        @Serializable
        data class PublicRoomsChunk(
            @SerialName("avatar_url") val avatarUrl: String? = null,
            @SerialName("canonical_alias") val canonicalAlias: RoomAliasId? = null,
            @SerialName("children_state") val childrenState: Set<@Contextual Event.StrippedStateEvent<*>>,
            @SerialName("guest_can_join") val guestCanJoin: Boolean,
            @SerialName("join_rule") val joinRule: JoinRulesEventContent.JoinRule = JoinRulesEventContent.JoinRule.Public,
            @SerialName("name") val name: String? = null,
            @SerialName("num_joined_members") val joinedMembersCount: Long,
            @SerialName("room_id") val roomId: RoomId,
            @SerialName("room_type") val roomType: CreateEventContent.RoomType? = null,
            @SerialName("topic") val topic: String? = null,
            @SerialName("world_readable") val worldReadable: Boolean,
        )
    }
}