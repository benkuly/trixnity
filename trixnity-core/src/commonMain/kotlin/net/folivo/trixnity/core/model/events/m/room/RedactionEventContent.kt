package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#mroomredaction">matrix spec</a>
 */
@Serializable
data class RedactionEventContent(
    @SerialName("redacts")
    val redacts: EventId,
    @SerialName("reason")
    val reason: String? = null,
) : MessageEventContent {
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo? = null
    override val mentions: Mentions? = null
}