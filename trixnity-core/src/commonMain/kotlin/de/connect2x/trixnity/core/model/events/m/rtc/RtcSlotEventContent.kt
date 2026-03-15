package de.connect2x.trixnity.core.model.events.m.rtc

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.StateEventContent

/**
 * MatrixRTC slot state event content.
 *
 * @see <a href="https://github.com/matrix-org/matrix-spec-proposals/pull/4143">MSC4143</a>
 */
@MSC4143
@Serializable
data class RtcSlotEventContent(
    @Contextual
    @SerialName("application")
    val application: RtcApplication? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent
