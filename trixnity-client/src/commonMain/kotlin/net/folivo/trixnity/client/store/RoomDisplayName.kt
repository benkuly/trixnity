package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.model.UserId

@Serializable
data class RoomDisplayName(
    val explicitName: String? = null,
    val isEmpty: Boolean = false,
    val otherUsersCount: Int = 0,
    internal val summary: RoomSummary?
) {
    val heroes: List<UserId>
        get() = summary?.heroes ?: listOf()
}