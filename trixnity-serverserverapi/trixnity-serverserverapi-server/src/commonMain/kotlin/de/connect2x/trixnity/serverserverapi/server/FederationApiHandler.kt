package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.serverserverapi.model.federation.*

interface FederationApiHandler {
    /**
     * @see [SendTransaction]
     */
    suspend fun sendTransaction(context: MatrixEndpointContext<SendTransaction, SendTransaction.Request, SendTransaction.Response>): SendTransaction.Response

    /**
     * @see [GetEventAuthChain]
     */
    suspend fun getEventAuthChain(context: MatrixEndpointContext<GetEventAuthChain, Unit, GetEventAuthChain.Response>): GetEventAuthChain.Response

    /**
     * @see [BackfillRoom]
     */
    suspend fun backfillRoom(context: MatrixEndpointContext<BackfillRoom, Unit, PduTransaction>): PduTransaction

    /**
     * @see [GetMissingEvents]
     */
    suspend fun getMissingEvents(context: MatrixEndpointContext<GetMissingEvents, GetMissingEvents.Request, PduTransaction>): PduTransaction

    /**
     * @see [GetEvent]
     */
    suspend fun getEvent(context: MatrixEndpointContext<GetEvent, Unit, PduTransaction>): PduTransaction

    /**
     * @see [GetState]
     */
    suspend fun getState(context: MatrixEndpointContext<GetState, Unit, GetState.Response>): GetState.Response

    /**
     * @see [GetStateIds]
     */
    suspend fun getStateIds(context: MatrixEndpointContext<GetStateIds, Unit, GetStateIds.Response>): GetStateIds.Response

    /**
     * @see [MakeJoin]
     */
    suspend fun makeJoin(context: MatrixEndpointContext<MakeJoin, Unit, MakeJoin.Response>): MakeJoin.Response

    /**
     * @see [SendJoin]
     */
    suspend fun sendJoin(context: MatrixEndpointContext<SendJoin, Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendJoin.Response>): SendJoin.Response

    /**
     * @see [MakeKnock]
     */
    suspend fun makeKnock(context: MatrixEndpointContext<MakeKnock, Unit, MakeKnock.Response>): MakeKnock.Response

    /**
     * @see [SendKnock]
     */
    suspend fun sendKnock(context: MatrixEndpointContext<SendKnock, Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendKnock.Response>): SendKnock.Response

    /**
     * @see [Invite]
     */
    suspend fun invite(context: MatrixEndpointContext<Invite, Invite.Request, Invite.Response>): Invite.Response

    /**
     * @see [MakeLeave]
     */
    suspend fun makeLeave(context: MatrixEndpointContext<MakeLeave, Unit, MakeLeave.Response>): MakeLeave.Response

    /**
     * @see [SendLeave]
     */
    suspend fun sendLeave(context: MatrixEndpointContext<SendLeave, Signed<PersistentStateDataUnit<MemberEventContent>, String>, Unit>)

    /**
     * @see [OnBindThirdPid]
     */
    suspend fun onBindThirdPid(context: MatrixEndpointContext<OnBindThirdPid, OnBindThirdPid.Request, Unit>)

    /**
     * @see [ExchangeThirdPartyInvite]
     */
    suspend fun exchangeThirdPartyInvite(context: MatrixEndpointContext<ExchangeThirdPartyInvite, Signed<PersistentStateDataUnit<MemberEventContent>, String>, Unit>)

    /**
     * @see [GetPublicRooms]
     */
    suspend fun getPublicRooms(context: MatrixEndpointContext<GetPublicRooms, Unit, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see [GetPublicRoomsWithFilter]
     */
    suspend fun getPublicRoomsWithFilter(context: MatrixEndpointContext<GetPublicRoomsWithFilter, GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see [GetHierarchy]
     */
    suspend fun getHierarchy(context: MatrixEndpointContext<GetHierarchy, Unit, GetHierarchy.Response>): GetHierarchy.Response

    /**
     * @see [QueryDirectory]
     */
    suspend fun queryDirectory(context: MatrixEndpointContext<QueryDirectory, Unit, QueryDirectory.Response>): QueryDirectory.Response

    /**
     * @see [QueryProfile]
     */
    suspend fun queryProfile(context: MatrixEndpointContext<QueryProfile, Unit, QueryProfile.Response>): QueryProfile.Response

    /**
     * @see [GetOIDCUserInfo]
     */
    suspend fun getOIDCUserInfo(context: MatrixEndpointContext<GetOIDCUserInfo, Unit, GetOIDCUserInfo.Response>): GetOIDCUserInfo.Response

    /**
     * @see [GetDevices]
     */
    suspend fun getDevices(context: MatrixEndpointContext<GetDevices, Unit, GetDevices.Response>): GetDevices.Response

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(context: MatrixEndpointContext<ClaimKeys, ClaimKeys.Request, ClaimKeys.Response>): ClaimKeys.Response

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(context: MatrixEndpointContext<GetKeys, GetKeys.Request, GetKeys.Response>): GetKeys.Response

    /**
     * @see [TimestampToEvent]
     */
    suspend fun timestampToEvent(context: MatrixEndpointContext<TimestampToEvent, Unit, TimestampToEvent.Response>): TimestampToEvent.Response

    /**
     * @see [DownloadMedia]
     */
    suspend fun downloadMedia(context: MatrixEndpointContext<DownloadMedia, Unit, Media>): Media

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun downloadThumbnail(context: MatrixEndpointContext<DownloadThumbnail, Unit, Media>): Media
}