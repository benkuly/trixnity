package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class RoomsKeyBackup<T : RoomKeyBackupSessionData>(
    @SerialName("rooms")
    val rooms: Map<RoomId, RoomKeyBackup<T>>,
)