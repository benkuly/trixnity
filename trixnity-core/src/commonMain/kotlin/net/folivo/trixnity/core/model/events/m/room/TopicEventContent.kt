package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomtopic">matrix spec</a>
 */
@Serializable
data class TopicEventContent(
    @SerialName("topic")
    val topic: String,
    @SerialName("external_url")
    override val externalUrl: String? = null
) : StateEventContent