package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId.UserId

data class RoomDisplayName(
    val explicitName: String? = null,
    val isEmpty: Boolean = false,
    val heroes: List<UserId> = listOf(),
    val otherUsersCount: Int = 0
)