package net.folivo.trixnity.core.model.events.m.call

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Purpose {
    @SerialName("m.usermedia") USERMEDIA,
    @SerialName("m.screenshare") SCREENSHARE,
}

@Serializable
data class StreamMetadata(
    @SerialName("purpose") val purpose: Purpose,

    // Added in v1.11:

    @SerialName("audio_muted") val audioMuted: Boolean? = false,
    @SerialName("video_muted") val videoMuted: Boolean? = false,
)
