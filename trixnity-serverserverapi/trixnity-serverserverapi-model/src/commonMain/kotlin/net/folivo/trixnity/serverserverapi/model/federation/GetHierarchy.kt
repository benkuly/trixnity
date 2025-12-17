package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1hierarchyroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/hierarchy/{roomId}")
@HttpMethod(GET)
data class GetHierarchy(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("suggested_only") val suggestedOnly: Boolean = false,
) : MatrixEndpoint<Unit, GetHierarchy.Response> {
    @Serializable
    data class Response(
        @SerialName("children") val rooms: List<SpaceHierarchyRoomsChunk>,
        @SerialName("inaccessible_children") val inaccessible_children: Set<RoomId>,
        @SerialName("room") val room: SpaceHierarchyRoomsChunk,
    ) {
        @Serializable
        data class SpaceHierarchyRoomsChunk(
            @SerialName("allowed_room_ids") val allowedRoomIds: Set<RoomId>? = null,
            @SerialName("avatar_url") val avatarUrl: String? = null,
            @SerialName("canonical_alias") val canonicalAlias: RoomAliasId? = null,
            @SerialName("children_state") val childrenState: Set<@Contextual ClientEvent.StrippedStateEvent<*>>,
            @SerialName("encryption") val encryption: EncryptionAlgorithm? = null,
            @SerialName("guest_can_join") val guestCanJoin: Boolean,
            @SerialName("join_rule") val joinRule: JoinRulesEventContent.JoinRule = JoinRulesEventContent.JoinRule.Public,
            @SerialName("name") val name: String? = null,
            @SerialName("num_joined_members") val joinedMembersCount: Long,
            @SerialName("room_id") val roomId: RoomId,
            @SerialName("room_type") val roomType: CreateEventContent.RoomType? = null,
            @SerialName("room_version") val roomVersion: String? = null,
            @SerialName("topic") val topic: String? = null,
            @SerialName("world_readable") val worldReadable: Boolean,
        )
    }
}