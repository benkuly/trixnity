package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.MSC4354
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class StickyEventData @MSC4354 constructor(
    @MSC4354
    @SerialName("duration_ms")
    val durationMs: Long,
)
