package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent

interface UsersApiHandler {
    /**
     * @see [GetDisplayName]
     */
    suspend fun getDisplayName(context: MatrixEndpointContext<GetDisplayName, Unit, GetDisplayName.Response>): GetDisplayName.Response

    /**
     * @see [SetDisplayName]
     */
    suspend fun setDisplayName(context: MatrixEndpointContext<SetDisplayName, SetDisplayName.Request, Unit>)

    /**
     * @see [GetAvatarUrl]
     */
    suspend fun getAvatarUrl(context: MatrixEndpointContext<GetAvatarUrl, Unit, GetAvatarUrl.Response>): GetAvatarUrl.Response

    /**
     * @see [SetAvatarUrl]
     */
    suspend fun setAvatarUrl(context: MatrixEndpointContext<SetAvatarUrl, SetAvatarUrl.Request, Unit>)

    /**
     * @see [GetProfile]
     */
    suspend fun getProfile(context: MatrixEndpointContext<GetProfile, Unit, GetProfile.Response>): GetProfile.Response

    /**
     * @see [GetPresence]
     */
    suspend fun getPresence(context: MatrixEndpointContext<GetPresence, Unit, PresenceEventContent>): PresenceEventContent

    /**
     * @see [SetPresence]
     */
    suspend fun setPresence(context: MatrixEndpointContext<SetPresence, SetPresence.Request, Unit>)

    /**
     * @see [SendToDevice]
     */
    suspend fun sendToDevice(context: MatrixEndpointContext<SendToDevice, SendToDevice.Request, Unit>)

    /**
     * @see [GetFilter]
     */
    suspend fun getFilter(context: MatrixEndpointContext<GetFilter, Unit, Filters>): Filters

    /**
     * @see [SetFilter]
     */
    suspend fun setFilter(context: MatrixEndpointContext<SetFilter, Filters, SetFilter.Response>): SetFilter.Response

    /**
     * @see [GetGlobalAccountData]
     */
    suspend fun getAccountData(context: MatrixEndpointContext<GetGlobalAccountData, Unit, GlobalAccountDataEventContent>): GlobalAccountDataEventContent

    /**
     * @see [SetGlobalAccountData]
     */
    suspend fun setAccountData(context: MatrixEndpointContext<SetGlobalAccountData, GlobalAccountDataEventContent, Unit>): Unit

    /**
     *  @see [SearchUsers]
     */
    suspend fun searchUsers(context: MatrixEndpointContext<SearchUsers, SearchUsers.Request, SearchUsers.Response>): SearchUsers.Response

    /**
     * @see [ReportUser]
     */
    suspend fun reportUser(context: MatrixEndpointContext<ReportUser, ReportUser.Request, Unit>)
}