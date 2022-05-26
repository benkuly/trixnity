package net.folivo.trixnity.serverserverapi.client

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
    suspend fun sendTransaction(txnId: String, request: SendTransaction.Request): Result<SendTransaction.Response>

    /**
     * @see [GetEventAuthChain]
     */
    suspend fun getEventAuthChain(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetEventAuthChain.Response>

    /**
     * @see [BackfillRoom]
     */
    suspend fun backfillRoom(
        roomId: RoomId,
        startFrom: List<EventId>,
        limit: Long
    ): Result<PduTransaction>

    /**
     * @see [GetMissingEvents]
     */
    suspend fun getMissingEvents(
        roomId: RoomId, request:
        GetMissingEvents.Request
    ): Result<PduTransaction>

    /**
     * @see [GetEvent]
     */
    suspend fun getEvent(eventId: EventId): Result<PduTransaction>

    /**
     * @see [GetState]
     */
    suspend fun getState(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetState.Response>

    /**
     * @see [GetStateIds]
     */
    suspend fun getStateIds(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetStateIds.Response>

    /**
     * @see [MakeJoin]
     */
    suspend fun makeJoin(
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>? = null
    ): Result<MakeJoin.Response>

    /**
     * @see [SendJoin]
     */
    suspend fun sendJoin(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendJoin.Response>

    /**
     * @see [MakeKnock]
     */
    suspend fun makeKnock(
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>? = null
    ): Result<MakeKnock.Response>

    /**
     * @see [SendKnock]
     */
    suspend fun sendKnock(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendKnock.Response>

    /**
     * @see [Invite]
     */
    suspend fun invite(
        roomId: RoomId,
        eventId: EventId,
        request: Invite.Request
    ): Result<Invite.Response>

    /**
     * @see [MakeLeave]
     */
    suspend fun makeLeave(
        roomId: RoomId,
        userId: UserId
    ): Result<MakeLeave.Response>

    /**
     * @see [SendLeave]
     */
    suspend fun sendLeave(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit>

    /**
     * @see [OnBindThirdPid]
     */
    suspend fun onBindThirdPid(request: OnBindThirdPid.Request): Result<Unit>

    /**
     * @see [ExchangeThirdPartyInvite]
     */
    suspend fun exchangeThirdPartyInvite(
        roomId: RoomId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit>

    /**
     * @see [GetPublicRooms]
     */
    suspend fun getPublicRooms(
        includeAllNetworks: Boolean? = null,
        limit: Long? = null,
        since: String? = null,
        thirdPartyInstanceId: String? = null
    ): Result<GetPublicRoomsResponse>

    /**
     * @see [GetPublicRoomsWithFilter]
     */
    suspend fun getPublicRoomsWithFilter(request: GetPublicRoomsWithFilter.Request): Result<GetPublicRoomsResponse>

    /**
     * @see [GetHierarchy]
     */
    suspend fun getHierarchy(
        roomId: RoomId,
        suggestedOnly: Boolean = false
    ): Result<GetHierarchy.Response>

    /**
     * @see [QueryDirectory]
     */
    suspend fun queryDirectory(roomAlias: RoomAliasId): Result<QueryDirectory.Response>

    /**
     * @see [QueryProfile]
     */
    suspend fun queryProfile(
        userId: UserId,
        field: QueryProfile.Field? = null
    ): Result<QueryProfile.Response>

    /**
     * @see [GetOIDCUserInfo]
     */
    suspend fun getOIDCUserInfo(accessToken: String): Result<GetOIDCUserInfo.Response>

    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(userId: UserId): Result<GetDevices.Response>

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(request: ClaimKeys.Request): Result<ClaimKeys.Response>

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(request: GetKeys.Request): Result<GetKeys.Response>
}

class FederationApiClient(
    private val httpClient: MatrixApiClient
) : IFederationApiClient {
    override suspend fun sendTransaction(
        txnId: String,
        request: SendTransaction.Request
    ): Result<SendTransaction.Response> =
        httpClient.request(SendTransaction(txnId), request)

    override suspend fun getEventAuthChain(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetEventAuthChain.Response> =
        httpClient.request(GetEventAuthChain(roomId, eventId))

    override suspend fun backfillRoom(
        roomId: RoomId,
        startFrom: List<EventId>,
        limit: Long
    ): Result<PduTransaction> =
        httpClient.request(BackfillRoom(roomId, startFrom, limit))

    override suspend fun getMissingEvents(
        roomId: RoomId, request:
        GetMissingEvents.Request
    ): Result<PduTransaction> =
        httpClient.request(GetMissingEvents(roomId), request)

    override suspend fun getEvent(eventId: EventId): Result<PduTransaction> =
        httpClient.request(GetEvent(eventId))

    override suspend fun getState(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetState.Response> =
        httpClient.request(GetState(roomId, eventId))

    override suspend fun getStateIds(
        roomId: RoomId,
        eventId: EventId
    ): Result<GetStateIds.Response> =
        httpClient.request(GetStateIds(roomId, eventId))

    override suspend fun makeJoin(
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeJoin.Response> =
        httpClient.request(MakeJoin(roomId, userId, supportedRoomVersions))

    override suspend fun sendJoin(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendJoin.Response> =
        httpClient.request(SendJoin(roomId, eventId), request)

    override suspend fun makeKnock(
        roomId: RoomId,
        userId: UserId,
        supportedRoomVersions: Set<String>?
    ): Result<MakeKnock.Response> =
        httpClient.request(MakeKnock(roomId, userId, supportedRoomVersions))

    override suspend fun sendKnock(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<SendKnock.Response> =
        httpClient.request(SendKnock(roomId, eventId), request)

    override suspend fun invite(
        roomId: RoomId,
        eventId: EventId,
        request: Invite.Request
    ): Result<Invite.Response> =
        httpClient.request(Invite(roomId, eventId), request)

    override suspend fun makeLeave(
        roomId: RoomId,
        userId: UserId
    ): Result<MakeLeave.Response> =
        httpClient.request(MakeLeave(roomId, userId))

    override suspend fun sendLeave(
        roomId: RoomId,
        eventId: EventId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        httpClient.request(SendLeave(roomId, eventId), request)

    override suspend fun onBindThirdPid(request: OnBindThirdPid.Request): Result<Unit> =
        httpClient.request(OnBindThirdPid, request)

    override suspend fun exchangeThirdPartyInvite(
        roomId: RoomId,
        request: Signed<PersistentDataUnit.PersistentStateDataUnit<MemberEventContent>, String>
    ): Result<Unit> =
        httpClient.request(ExchangeThirdPartyInvite(roomId), request)

    override suspend fun getPublicRooms(
        includeAllNetworks: Boolean?,
        limit: Long?,
        since: String?,
        thirdPartyInstanceId: String?
    ): Result<GetPublicRoomsResponse> =
        httpClient.request(GetPublicRooms(includeAllNetworks, limit, since, thirdPartyInstanceId))

    override suspend fun getPublicRoomsWithFilter(request: GetPublicRoomsWithFilter.Request): Result<GetPublicRoomsResponse> =
        httpClient.request(GetPublicRoomsWithFilter, request)

    override suspend fun getHierarchy(
        roomId: RoomId,
        suggestedOnly: Boolean
    ): Result<GetHierarchy.Response> =
        httpClient.request(GetHierarchy(roomId, suggestedOnly))

    override suspend fun queryDirectory(roomAlias: RoomAliasId): Result<QueryDirectory.Response> =
        httpClient.request(QueryDirectory(roomAlias))

    override suspend fun queryProfile(
        userId: UserId,
        field: QueryProfile.Field?
    ): Result<QueryProfile.Response> =
        httpClient.request(QueryProfile(userId, field))

    override suspend fun getOIDCUserInfo(accessToken: String): Result<GetOIDCUserInfo.Response> =
        httpClient.request(GetOIDCUserInfo(accessToken))

    override suspend fun getDevices(userId: UserId): Result<GetDevices.Response> =
        httpClient.request(GetDevices(userId))

    override suspend fun claimKeys(request: ClaimKeys.Request): Result<ClaimKeys.Response> =
        httpClient.request(ClaimKeys, request)

    override suspend fun getKeys(request: GetKeys.Request): Result<GetKeys.Response> =
        httpClient.request(GetKeys, request)
}