package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.json.JsonObject

/**
 * @see <a href="https://github.com/matrix-org/matrix-spec-proposals/pull/4143">MSC4143</a>
 */
@MSC4143
interface RtcApplication

@MSC4143
data class UnknownRtcApplication(val raw: JsonObject) : RtcApplication
