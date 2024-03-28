package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mtyping">matrix spec</a>
 */
@Serializable
data class TypingEventContent(
    @SerialName("user_ids") val users: Set<UserId>,
) : EphemeralEventContent