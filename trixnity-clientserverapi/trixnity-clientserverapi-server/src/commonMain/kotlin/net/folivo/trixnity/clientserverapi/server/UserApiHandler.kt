package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.user.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent

interface UserApiHandler {
    /**
     * @see [GetProfileField]
     */
    suspend fun getProfileField(context: MatrixEndpointContext<GetProfileField, Unit, ProfileField>): ProfileField

    /**
     * @see [SetProfileField]
     */
    suspend fun setProfileField(context: MatrixEndpointContext<SetProfileField, ProfileField, Unit>)

    /**
     * @see [DeleteProfileField]
     */
    suspend fun deleteProfileField(context: MatrixEndpointContext<DeleteProfileField, Unit, Unit>)

    /**
     * @see [GetProfile]
     */
    suspend fun getProfile(context: MatrixEndpointContext<GetProfile, Unit, Profile>): Profile

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