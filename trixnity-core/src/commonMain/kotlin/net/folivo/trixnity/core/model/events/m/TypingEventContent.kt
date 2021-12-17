package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent

@Serializable
data class TypingEventContent(
    @SerialName("user_ids") val users: List<UserId>,
) : EphemeralEventContent {
}