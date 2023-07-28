package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.model.events.MessageEventContent

@Serializable
data class ReactionEventContent(
    @SerialName("m.relates_to") override val relatesTo: RelatesTo.Annotation? = null,
) : MessageEventContent {
    @Transient
    override val mentions: Mentions? = null
}