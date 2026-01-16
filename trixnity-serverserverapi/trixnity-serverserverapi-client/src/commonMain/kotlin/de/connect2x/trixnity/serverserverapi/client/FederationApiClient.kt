package de.connect2x.trixnity.serverserverapi.client

import io.ktor.client.plugins.*
import io.ktor.http.*
import de.connect2x.trixnity.api.client.MatrixApiClient
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.serverserverapi.model.federation.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FederationApiClient {
    /**
     * @see [SendTransaction]
     */
    suspend fun sendTransaction(
        baseUrl: Url,
        txnId: String,
        request: SendTransaction.Request
    ): Result<SendTransaction.Response>

    /**
     * @see [GetEventAuthChain]
     */
    suspend fun getEventAuthChain(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetEventAuthChain.Response>

    /**
     * @see [BackfillRoom]
     */
    suspend fun backfillRoom(
        baseUrl: Url,
        roomId: RoomId,
        startFrom: List<EventId>,
        limit: Long
    ): Result<PduTransaction>

    /**
     * @see [GetMissingEvents]
     */
    suspend fun getMissingEvents(
        baseUrl: Url,
        roomId: RoomId, request:
        GetMissingEvents.Request
    ): Result<PduTransaction>

    /**
     * @see [GetEvent]
     */
    suspend fun getEvent(baseUrl: Url, eventId: EventId): Result<PduTransaction>

    /**
     * @see [GetState]
     */
    suspend fun getState(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetState.Response>

    /**
     * @see [GetStateIds]
     */
    suspend fun getStateIds(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetStateIds.Response>

    /**
     * @see [MakeJoin]
     */
    suspend fun makeJoin(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>? = null
    ): Result<MakeJoin.Response>

    /**
     * @see [SendJoin]
     */
    suspend fun sendJoin(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendJoin.Response>

    /**
     * @see [MakeKnock]
     */
    suspend fun makeKnock(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>? = null
    ): Result<MakeKnock.Response>

    /**
     * @see [SendKnock]
     */
    suspend fun sendKnock(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendKnock.Response>

    /**
     * @see [Invite]
     */
    suspend fun invite(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Invite.Request
    ): Result<Invite.Response>

    /**
     * @see [MakeLeave]
     */
    suspend fun makeLeave(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId
    ): Result<MakeLeave.Response>

    /**
     * @see [SendLeave]
     */
    suspend fun sendLeave(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit>

    /**
     * @see [OnBindThirdPid]
     */
    suspend fun onBindThirdPid(baseUrl: Url, request: OnBindThirdPid.Request): Result<Unit>

    /**
     * @see [ExchangeThirdPartyInvite]
     */
    suspend fun exchangeThirdPartyInvite(
        baseUrl: Url,
        roomId: RoomId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit>

    /**
     * @see [GetPublicRooms]
     */
    suspend fun getPublicRooms(
        baseUrl: Url,
        includeAllNetworks: Boolean? = null,
        limit: Long? = null,
        since: String? = null,
        thirdPartyInstanceId: String? = null
    ): Result<GetPublicRoomsResponse>

    /**
     * @see [GetPublicRoomsWithFilter]
     */
    suspend fun getPublicRoomsWithFilter(
        baseUrl: Url,
        request: GetPublicRoomsWithFilter.Request
    ): Result<GetPublicRoomsResponse>

    /**
     * @see [GetHierarchy]
     */
    suspend fun getHierarchy(
        baseUrl: Url,
        roomId: RoomId,
        suggestedOnly: Boolean = false
    ): Result<GetHierarchy.Response>

    /**
     * @see [QueryDirectory]
     */
    suspend fun queryDirectory(baseUrl: Url, roomAlias: RoomAliasId): Result<QueryDirectory.Response>

    /**
     * @see [QueryProfile]
     */
    suspend fun queryProfile(
        baseUrl: Url,
        userId: UserId,
        field: QueryProfile.Field? = null
    ): Result<QueryProfile.Response>

    /**
     * @see [GetOIDCUserInfo]
     */
    suspend fun getOIDCUserInfo(baseUrl: Url, accessToken: String): Result<GetOIDCUserInfo.Response>

    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(baseUrl: Url, userId: UserId): Result<GetDevices.Response>

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(baseUrl: Url, request: ClaimKeys.Request): Result<ClaimKeys.Response>

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(baseUrl: Url, request: GetKeys.Request): Result<GetKeys.Response>

    /**
     * @see [TimestampToEvent]
     */
    suspend fun timestampToEvent(
        roomId: RoomId,
        timestamp: Long,
        dir: TimestampToEvent.Direction = TimestampToEvent.Direction.FORWARDS,
    ): Result<TimestampToEvent.Response>

    /**
     * @see [DownloadMedia]
     */
    suspend fun downloadMedia(
        mediaId: String,
        timeout: Duration? = null,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit>

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun downloadThumbnail(
        mediaId: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean? = null,
        timeout: Duration? = null,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit>
}

class FederationApiClientImpl(
    private val baseClient: MatrixApiClient
) : FederationApiClient {
    override suspend fun sendTransaction(
        baseUrl: Url,
        txnId: String,
        request: SendTransaction.Request
    ): Result<SendTransaction.Response> =
        baseClient.request(SendTransaction(txnId), request) { mergeUrl(baseUrl) }

    override suspend fun getEventAuthChain(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetEventAuthChain.Response> =
        baseClient.request(GetEventAuthChain(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun backfillRoom(
        baseUrl: Url,
        roomId: RoomId,
        startFrom: List<EventId>,
        limit: Long
    ): Result<PduTransaction> =
        baseClient.request(BackfillRoom(roomId, startFrom, limit)) { mergeUrl(baseUrl) }

    override suspend fun getMissingEvents(
        baseUrl: Url,
        roomId: RoomId, request:
        GetMissingEvents.Request
    ): Result<PduTransaction> =
        baseClient.request(GetMissingEvents(roomId), request) { mergeUrl(baseUrl) }

    override suspend fun getEvent(baseUrl: Url, eventId: EventId): Result<PduTransaction> =
        baseClient.request(GetEvent(eventId)) { mergeUrl(baseUrl) }

    override suspend fun getState(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetState.Response> =
        baseClient.request(GetState(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun getStateIds(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetStateIds.Response> =
        baseClient.request(GetStateIds(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun makeJoin(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeJoin.Response> =
        baseClient.request(MakeJoin(roomId, userId, supportedRoomVersions)) { mergeUrl(baseUrl) }

    override suspend fun sendJoin(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendJoin.Response> =
        baseClient.request(SendJoin(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun makeKnock(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeKnock.Response> =
        baseClient.request(MakeKnock(roomId, userId, supportedRoomVersions)) { mergeUrl(baseUrl) }

    override suspend fun sendKnock(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendKnock.Response> =
        baseClient.request(SendKnock(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun invite(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Invite.Request
    ): Result<Invite.Response> =
        baseClient.request(Invite(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun makeLeave(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId
    ): Result<MakeLeave.Response> =
        baseClient.request(MakeLeave(roomId, userId)) { mergeUrl(baseUrl) }

    override suspend fun sendLeave(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        baseClient.request(SendLeave(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun onBindThirdPid(baseUrl: Url, request: OnBindThirdPid.Request): Result<Unit> =
        baseClient.request(OnBindThirdPid, request) { mergeUrl(baseUrl) }

    override suspend fun exchangeThirdPartyInvite(
        baseUrl: Url,
        roomId: RoomId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        baseClient.request(ExchangeThirdPartyInvite(roomId), request) { mergeUrl(baseUrl) }

    override suspend fun getPublicRooms(
        baseUrl: Url,
        includeAllNetworks: Boolean?,
        limit: Long?,
        since: String?,
        thirdPartyInstanceId: String?
    ): Result<GetPublicRoomsResponse> =
        baseClient.request(GetPublicRooms(includeAllNetworks, limit, since, thirdPartyInstanceId)) { mergeUrl(baseUrl) }

    override suspend fun getPublicRoomsWithFilter(
        baseUrl: Url,
        request: GetPublicRoomsWithFilter.Request
    ): Result<GetPublicRoomsResponse> =
        baseClient.request(GetPublicRoomsWithFilter, request) { mergeUrl(baseUrl) }

    override suspend fun getHierarchy(
        baseUrl: Url,
        roomId: RoomId,
        suggestedOnly: Boolean
    ): Result<GetHierarchy.Response> =
        baseClient.request(GetHierarchy(roomId, suggestedOnly)) { mergeUrl(baseUrl) }

    override suspend fun queryDirectory(baseUrl: Url, roomAlias: RoomAliasId): Result<QueryDirectory.Response> =
        baseClient.request(QueryDirectory(roomAlias)) { mergeUrl(baseUrl) }

    override suspend fun queryProfile(
        baseUrl: Url,
        userId: UserId,
        field: QueryProfile.Field?
    ): Result<QueryProfile.Response> =
        baseClient.request(QueryProfile(userId, field)) { mergeUrl(baseUrl) }

    override suspend fun getOIDCUserInfo(baseUrl: Url, accessToken: String): Result<GetOIDCUserInfo.Response> =
        baseClient.request(GetOIDCUserInfo(accessToken)) { mergeUrl(baseUrl) }

    override suspend fun getDevices(baseUrl: Url, userId: UserId): Result<GetDevices.Response> =
        baseClient.request(GetDevices(userId)) { mergeUrl(baseUrl) }

    override suspend fun claimKeys(baseUrl: Url, request: ClaimKeys.Request): Result<ClaimKeys.Response> =
        baseClient.request(ClaimKeys, request) { mergeUrl(baseUrl) }

    override suspend fun getKeys(baseUrl: Url, request: GetKeys.Request): Result<GetKeys.Response> =
        baseClient.request(GetKeys, request) { mergeUrl(baseUrl) }

    override suspend fun timestampToEvent(
        roomId: RoomId,
        timestamp: Long,
        dir: TimestampToEvent.Direction
    ): Result<TimestampToEvent.Response> =
        baseClient.request(TimestampToEvent(roomId, timestamp, dir))

    override suspend fun downloadMedia(
        mediaId: String,
        timeout: Duration?,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> =
        baseClient.withRequest(
            endpoint = DownloadMedia(mediaId, timeout?.inWholeMilliseconds),
            requestBuilder = {
                method = HttpMethod.Get
                timeout {
                    requestTimeoutMillis =
                        timeout?.plus(10.seconds)?.inWholeMilliseconds ?: Duration.INFINITE.inWholeMilliseconds
                }
            },
            responseHandler = downloadHandler
        )

    override suspend fun downloadThumbnail(
        mediaId: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean?,
        timeout: Duration?,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> =
        baseClient.withRequest(
            endpoint = DownloadThumbnail(
                mediaId = mediaId,
                width = width,
                height = height,
                method = method,
                animated = animated,
                timeoutMs = timeout?.inWholeMilliseconds
            ),
            requestBuilder = {
                this.method = HttpMethod.Get
                timeout {
                    requestTimeoutMillis =
                        timeout?.plus(10.seconds)?.inWholeMilliseconds ?: Duration.INFINITE.inWholeMilliseconds
                }
            },
            responseHandler = downloadHandler
        )
}