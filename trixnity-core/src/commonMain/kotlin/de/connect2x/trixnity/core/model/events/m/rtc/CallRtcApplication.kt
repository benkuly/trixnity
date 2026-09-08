package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data object CallRtcApplication {
    const val APPLICATION_TYPE = "m.call"
    val SLOT_ID = RtcSlotId(APPLICATION_TYPE, "room")

    @MSC4193 @MSC4143 @Serializable data object Slot : RtcApplicationSlot

    @MSC4193
    @MSC4143
    @Serializable
    data class Member(@SerialName("intent") val intent: Intent? = null) : RtcApplicationMember {

        @MSC4143
        enum class LeaveReasonCode(val value: String) {
            TRANSPORT_ERROR("transport_error"),
            MEDIA_ERROR("media_error"),
            CODE_MISMATCH("codec_mismatch"),
        }

        @Serializable
        enum class Intent {
            @SerialName("audio") AUDIO,
            @SerialName("video") VIDEO,
        }
    }
}
