package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomKeyBackup<T : RoomKeyBackupSessionData>(
    @SerialName("sessions")
    val sessions: Map<String, RoomKeyBackupData<T>>
)

