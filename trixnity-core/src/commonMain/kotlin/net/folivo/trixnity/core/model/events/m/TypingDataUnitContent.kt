package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#typing-notificationse">matrix spec</a>
 */
@Serializable
data class TypingDataUnitContent(
    @SerialName("room_id")
    val roomId: RoomId,
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("typing")
    val typing: Boolean,
) : EphemeralDataUnitContent