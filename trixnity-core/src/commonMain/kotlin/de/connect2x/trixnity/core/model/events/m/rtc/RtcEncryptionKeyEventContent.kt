package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@MSC4143
data class RtcEncryptionKeyEventContent(
    @SerialName("room_id") val roomId: RoomId,
    @SerialName("member_id") val memberId: RtcMemberId,
    @SerialName("media_key") val mediaKey: MediaKey,
) : ToDeviceEventContent {
    @Serializable
    data class MediaKey(
        @SerialName("key") val key: String,
        @SerialName("index") val index: Long,
        @SerialName("format") val format: String,
    )
}
