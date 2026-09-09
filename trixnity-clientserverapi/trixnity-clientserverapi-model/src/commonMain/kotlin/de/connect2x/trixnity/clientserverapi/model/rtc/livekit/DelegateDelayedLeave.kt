package de.connect2x.trixnity.clientserverapi.model.rtc.livekit

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import io.ktor.http.Url
import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@MSC4195
@Serializable
@Auth(AuthRequired.YES)
@Resource("/_matrix/client/v1/rtc/livekit/delegate_delayed_leave")
@HttpMethod(POST)
object DelegateDelayedLeave : MatrixEndpoint<DelegateDelayedLeave.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("url") val url: Url,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("slot_id") val slotId: RtcSlotId,
        @SerialName("member_id") val memberId: RtcMemberId,
        @SerialName("delay_id") val delayId: DelayId,
    )
}
