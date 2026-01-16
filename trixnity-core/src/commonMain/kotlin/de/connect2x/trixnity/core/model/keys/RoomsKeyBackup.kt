package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomId

@Serializable
data class RoomsKeyBackup(
    @SerialName("rooms")
    val rooms: Map<RoomId, RoomKeyBackup>,
)