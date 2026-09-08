package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import kotlinx.serialization.json.JsonObject

@MSC4143
interface RtcApplicationSlot {
    @MSC4143 data class Unknown(val type: String, val raw: JsonObject) : RtcApplicationSlot
}

@MSC4143
interface RtcApplicationMember {
    @MSC4143
    enum class DefaultLeaveReasonCode(val value: String) {
        LEAVE("leave"),
        DELAYED_LEAVE("delayed_leave"),
        SLOT_CLOSED("slot_closed"),
    }

    @MSC4143 data class Unknown(val type: String, val raw: JsonObject) : RtcApplicationMember
}
