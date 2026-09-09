package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.DelegateDelayedLeave
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.GetLiveKitToken
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import io.ktor.http.Url

@MSC4143
interface RtcApiClient {
    /** @see [GetTransports] */
    suspend fun getTransports(): Result<List<RtcTransport>>

    /** @see [GetLiveKitToken] + */
    @MSC4195
    suspend fun getLiveKitToken(
        serverName: String,
        url: Url,
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
    ): Result<String>

    /** @see [DelegateDelayedLeave] **/
    @MSC4195
    suspend fun delegateDelayedLeave(url: Url, roomId: RoomId, slotId: RtcSlotId, memberId: RtcMemberId, delayId: String): Result<Unit>
}

@MSC4143
class RtcApiClientImpl(private val baseClient: MatrixClientServerApiBaseClient) : RtcApiClient {
    override suspend fun getTransports(): Result<List<RtcTransport>> =
        baseClient.request(GetTransports).map { it.transports }

    @MSC4195
    override suspend fun getLiveKitToken(
        serverName: String,
        url: Url,
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
    ): Result<String> =
        baseClient.request(GetLiveKitToken, GetLiveKitToken.Request(serverName, url, roomId, slotId, memberId)).map {
            it.jwt
        }

    @MSC4195
    override suspend fun delegateDelayedLeave(
        url: Url,
        roomId: RoomId,
        slotId: RtcSlotId,
        memberId: RtcMemberId,
        delayId: String
    ): Result<Unit> = baseClient.request(DelegateDelayedLeave, DelegateDelayedLeave.Request(url, roomId, slotId, memberId, delayId))
}
