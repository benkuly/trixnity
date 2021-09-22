package net.folivo.trixnity.client.room

data class RoomDisplayName(
    val explicitName: String? = null,
    val isEmpty: Boolean = false,
    val heroesDisplayname: List<String> = listOf(),
    val otherUsersCount: Int = 0
)