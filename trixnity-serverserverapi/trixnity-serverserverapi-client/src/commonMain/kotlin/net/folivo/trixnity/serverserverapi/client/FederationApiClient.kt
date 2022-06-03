package net.folivo.trixnity.serverserverapi.client

import io.ktor.http.*
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.federation.*

interface IFederationApiClient {
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
}

class FederationApiClient(
    private val httpClient: MatrixApiClient
) : IFederationApiClient {
    override suspend fun sendTransaction(
        baseUrl: Url,
        txnId: String,
        request: SendTransaction.Request
    ): Result<SendTransaction.Response> =
        httpClient.request(SendTransaction(txnId), request) { mergeUrl(baseUrl) }

    override suspend fun getEventAuthChain(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetEventAuthChain.Response> =
        httpClient.request(GetEventAuthChain(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun backfillRoom(
        baseUrl: Url,
        roomId: RoomId,
        startFrom: List<EventId>,
        limit: Long
    ): Result<PduTransaction> =
        httpClient.request(BackfillRoom(roomId, startFrom, limit)) { mergeUrl(baseUrl) }

    override suspend fun getMissingEvents(
        baseUrl: Url,
        roomId: RoomId, request:
        GetMissingEvents.Request
    ): Result<PduTransaction> =
        httpClient.request(GetMissingEvents(roomId), request) { mergeUrl(baseUrl) }

    override suspend fun getEvent(baseUrl: Url, eventId: EventId): Result<PduTransaction> =
        httpClient.request(GetEvent(eventId)) { mergeUrl(baseUrl) }

    override suspend fun getState(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetState.Response> =
        httpClient.request(GetState(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun getStateIds(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId
    ): Result<GetStateIds.Response> =
        httpClient.request(GetStateIds(roomId, eventId)) { mergeUrl(baseUrl) }

    override suspend fun makeJoin(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeJoin.Response> =
        httpClient.request(MakeJoin(roomId, userId, supportedRoomVersions)) { mergeUrl(baseUrl) }

    override suspend fun sendJoin(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendJoin.Response> =
        httpClient.request(SendJoin(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun makeKnock(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeKnock.Response> =
        httpClient.request(MakeKnock(roomId, userId, supportedRoomVersions)) { mergeUrl(baseUrl) }

    override suspend fun sendKnock(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendKnock.Response> =
        httpClient.request(SendKnock(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun invite(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Invite.Request
    ): Result<Invite.Response> =
        httpClient.request(Invite(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun makeLeave(
        baseUrl: Url,
        roomId: RoomId,
        userId: UserId
    ): Result<MakeLeave.Response> =
        httpClient.request(MakeLeave(roomId, userId)) { mergeUrl(baseUrl) }

    override suspend fun sendLeave(
        baseUrl: Url,
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        httpClient.request(SendLeave(roomId, eventId), request) { mergeUrl(baseUrl) }

    override suspend fun onBindThirdPid(baseUrl: Url, request: OnBindThirdPid.Request): Result<Unit> =
        httpClient.request(OnBindThirdPid, request) { mergeUrl(baseUrl) }

    override suspend fun exchangeThirdPartyInvite(
        baseUrl: Url,
        roomId: RoomId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        httpClient.request(ExchangeThirdPartyInvite(roomId), request) { mergeUrl(baseUrl) }

    override suspend fun getPublicRooms(
        baseUrl: Url,
        includeAllNetworks: Boolean?,
        limit: Long?,
        since: String?,
        thirdPartyInstanceId: String?
    ): Result<GetPublicRoomsResponse> =
        httpClient.request(GetPublicRooms(includeAllNetworks, limit, since, thirdPartyInstanceId)) { mergeUrl(baseUrl) }

    override suspend fun getPublicRoomsWithFilter(
        baseUrl: Url,
        request: GetPublicRoomsWithFilter.Request
    ): Result<GetPublicRoomsResponse> =
        httpClient.request(GetPublicRoomsWithFilter, request) { mergeUrl(baseUrl) }

    override suspend fun getHierarchy(
        baseUrl: Url,
        roomId: RoomId,
        suggestedOnly: Boolean
    ): Result<GetHierarchy.Response> =
        httpClient.request(GetHierarchy(roomId, suggestedOnly)) { mergeUrl(baseUrl) }

    override suspend fun queryDirectory(baseUrl: Url, roomAlias: RoomAliasId): Result<QueryDirectory.Response> =
        httpClient.request(QueryDirectory(roomAlias)) { mergeUrl(baseUrl) }

    override suspend fun queryProfile(
        baseUrl: Url,
        userId: UserId,
        field: QueryProfile.Field?
    ): Result<QueryProfile.Response> =
        httpClient.request(QueryProfile(userId, field)) { mergeUrl(baseUrl) }

    override suspend fun getOIDCUserInfo(baseUrl: Url, accessToken: String): Result<GetOIDCUserInfo.Response> =
        httpClient.request(GetOIDCUserInfo(accessToken)) { mergeUrl(baseUrl) }

    override suspend fun getDevices(baseUrl: Url, userId: UserId): Result<GetDevices.Response> =
        httpClient.request(GetDevices(userId)) { mergeUrl(baseUrl) }

    override suspend fun claimKeys(baseUrl: Url, request: ClaimKeys.Request): Result<ClaimKeys.Response> =
        httpClient.request(ClaimKeys, request) { mergeUrl(baseUrl) }

    override suspend fun getKeys(baseUrl: Url, request: GetKeys.Request): Result<GetKeys.Response> =
        httpClient.request(GetKeys, request) { mergeUrl(baseUrl) }
}