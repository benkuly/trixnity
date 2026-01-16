package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import de.connect2x.trixnity.core.model.events.MessageEventContent

@Serializable
data class ReactionEventContent(
    @SerialName("m.relates_to") override val relatesTo: RelatesTo.Annotation? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
) : MessageEventContent {
    @Transient
    override val mentions: Mentions? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Annotation)
}