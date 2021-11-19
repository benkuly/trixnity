package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class RoomDisplayName(
    val explicitName: String? = null,
    val isEmpty: Boolean = false,
    val heroes: List<UserId> = listOf(),
    val otherUsersCount: Int = 0
)