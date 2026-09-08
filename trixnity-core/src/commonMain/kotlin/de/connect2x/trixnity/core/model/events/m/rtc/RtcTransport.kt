package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.json.JsonObject

@MSC4143
interface RtcTransport {
    @MSC4143 data class Unknown(val type: String, val raw: JsonObject) : RtcTransport
}
