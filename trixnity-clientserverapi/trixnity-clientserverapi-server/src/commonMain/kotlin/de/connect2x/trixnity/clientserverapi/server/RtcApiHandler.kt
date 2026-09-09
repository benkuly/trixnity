package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.GetLiveKitToken
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import io.ktor.http.Url
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.DelegateDelayedLeave

@MSC4143
interface RtcApiHandler {
    /** @see [GetTransports] */
    suspend fun getTransports(
        context: MatrixEndpointContext<GetTransports, Unit, GetTransports.Response>
    ): GetTransports.Response

    /** @see [GetLiveKitToken] **/
    @MSC4195
    suspend fun getLiveKitToken(
        context: MatrixEndpointContext<GetLiveKitToken, GetLiveKitToken.Request, GetLiveKitToken.Response>
    ): GetLiveKitToken.Response

    /** @see [DelegateDelayedLeave] **/
    @MSC4195
    suspend fun delegateDelayedLeave(
        context: MatrixEndpointContext<DelegateDelayedLeave, DelegateDelayedLeave.Request, Unit>
    )
}
