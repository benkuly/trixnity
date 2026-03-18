package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MatrixRTC membership content.
 *
 * @see <a href="https://github.com/matrix-org/matrix-spec-proposals/pull/4143">MSC4143</a>
 */
@MSC4143
@Serializable
data class RtcMemberEventContent(
    @SerialName("slot_id")
    val slotId: String,
    @Contextual
    @SerialName("application")
    val application: RtcApplication? = null,
    @SerialName("member")
    val member: Member? = null,
    @SerialName("rtc_transports")
    val rtcTransports: List<RtcTransport>? = null,
    @SerialName("sticky_key")
    val stickyKey: String? = null,
    @SerialName("versions")
    val versions: List<String>? = null,
    @SerialName("disconnect_reason")
    val disconnectReason: DisconnectReason? = null,
    @SerialName("disconnected")
    val disconnected: Boolean? = null,
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo? = null,
) : MessageEventContent {

    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    @MSC4143
    @Serializable
    data class Member(
        @SerialName("id")
        val id: String? = null,
        @SerialName("claimed_device_id")
        val claimedDeviceId: String? = null,
        @SerialName("claimed_user_id")
        val claimedUserId: UserId? = null,
    )

    @MSC4143
    @Serializable
    data class RtcTransport(
        // TODO should be extensible similar to [RtcApplication]
        @SerialName("type")
        val type: String,
    )

    @MSC4143
    @Serializable
    data class DisconnectReason(
        @SerialName("class")
        val klass: String? = null,
        @SerialName("reason")
        val reason: String? = null,
        @SerialName("description")
        val description: String? = null,
    )

    @MSC4143
    override fun copyWith(relatesTo: RelatesTo?): MessageEventContent = copy(relatesTo = relatesTo)
}
