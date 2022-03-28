package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent

interface UsersApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun getDisplayName(context: MatrixEndpointContext<GetDisplayName, Unit, GetDisplayName.Response>): GetDisplayName.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseriddisplayname">matrix spec</a>
     */
    suspend fun setDisplayName(context: MatrixEndpointContext<SetDisplayName, SetDisplayName.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun getAvatarUrl(context: MatrixEndpointContext<GetAvatarUrl, Unit, GetAvatarUrl.Response>): GetAvatarUrl.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3profileuseridavatar_url">matrix spec</a>
     */
    suspend fun setAvatarUrl(context: MatrixEndpointContext<SetAvatarUrl, SetAvatarUrl.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3profileuserid">matrix spec</a>
     */
    suspend fun getProfile(context: MatrixEndpointContext<GetProfile, Unit, GetProfile.Response>): GetProfile.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun getPresence(context: MatrixEndpointContext<GetPresence, Unit, PresenceEventContent>): PresenceEventContent

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
     */
    suspend fun setPresence(context: MatrixEndpointContext<SetPresence, SetPresence.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3sendtodeviceeventtypetxnid">matrix spec</a>
     */
    suspend fun sendToDevice(context: MatrixEndpointContext<SendToDevice, SendToDevice.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
     */
    suspend fun getFilter(context: MatrixEndpointContext<GetFilter, Unit, Filters>): Filters

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3useruseridfilter">matrix spec</a>
     */
    suspend fun setFilter(context: MatrixEndpointContext<SetFilter, Filters, SetFilter.Response>): SetFilter.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun getAccountData(context: MatrixEndpointContext<GetGlobalAccountData, Unit, GlobalAccountDataEventContent>): GlobalAccountDataEventContent

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
     */
    suspend fun setAccountData(context: MatrixEndpointContext<SetGlobalAccountData, GlobalAccountDataEventContent, Unit>): Unit

    /**
     *  @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3user_directorysearch">matrix spec</a>
     */
    suspend fun searchUsers(context: MatrixEndpointContext<SearchUsers, SearchUsers.Request, SearchUsers.Response>): SearchUsers.Response
}