package de.connect2x.trixnity.serverserverapi.model.federation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.JoinRulesEventContent

@Serializable
data class GetPublicRoomsResponse(
    @SerialName("chunk") val chunk: List<PublicRoomsChunk>,
    @SerialName("next_batch") val nextBatch: String? = null,
    @SerialName("prev_batch") val prevBatch: String? = null,
    @SerialName("total_room_count_estimate") val totalRoomCountEstimate: Long? = null
) {
    @Serializable
    data class PublicRoomsChunk(
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("canonical_alias") val canonicalAlias: RoomAliasId? = null,
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