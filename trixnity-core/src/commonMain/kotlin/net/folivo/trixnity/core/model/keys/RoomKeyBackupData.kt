package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomKeyBackupData<T : RoomKeyBackupSessionData>(
    @SerialName("first_message_index")
    val firstMessageIndex: Int,
    @SerialName("forwarded_count")
    val forwardedCount: Int,
    @SerialName("is_verified")
    val isVerified: Boolean,
    @SerialName("session_data")
    val sessionData: T,
)