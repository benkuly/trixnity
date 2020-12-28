package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEvent.CanonicalAliasEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-aliases">matrix spec</a>
 */
@Serializable
data class CanonicalAliasEvent(
    @SerialName("content") override val content: CanonicalAliasEventContent,
    @SerialName("sender") override val sender: UserId,
    @SerialName("event_id") override val id: EventId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("prev_content") override val previousContent: CanonicalAliasEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.aliases",
) : StateEvent<CanonicalAliasEventContent> {

    @Serializable
    data class CanonicalAliasEventContent(
        @SerialName("alias")
        val alias: RoomAliasId? = null,
        @SerialName("alt_aliases")
        val aliases: List<RoomAliasId> = listOf()
    )
}