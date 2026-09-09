package de.connect2x.trixnity.clientserverapi.model.rtc.livekit

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import io.ktor.http.Url
import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@MSC4195
@Serializable
@Auth(AuthRequired.YES)
@Resource("/_matrix/client/unstable/io.element.msc4195/rtc/livekit/get_token")
@HttpMethod(POST)
object GetLiveKitToken : MatrixEndpoint<GetLiveKitToken.Request, GetLiveKitToken.Response> {
    @Serializable
    data class Request(
        @SerialName("server_name") val serverName: String,
        @SerialName("url") val url: Url,
        @SerialName("room_id") val roomId: RoomId,
        @SerialName("slot_id") val slotId: RtcSlotId,
        @SerialName("member_id") val memberId: RtcMemberId,
    )

    @Serializable data class Response(val jwt: String)
}
