package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://github.com/matrix-org/matrix-spec-proposals/pull/4143">MSC4143</a>
 */
@MSC4143
@Serializable
data class CallApplication(
    @SerialName("m.call.id")
    val callId: String? = null,
    @SerialName("scope")
    val scope: String? = null,
) : RtcApplication
