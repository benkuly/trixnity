package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs

interface ServerApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(content: MatrixEndpointContext<GetVersions, Unit, GetVersions.Response>): GetVersions.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(context: MatrixEndpointContext<GetCapabilities, Unit, GetCapabilities.Response>): GetCapabilities.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3search">matrix spec</a>
     */
    suspend fun search(context: MatrixEndpointContext<Search, Search.Request, Search.Response>): Search.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3adminwhoisuserid">matrix spec</a>
     */
    suspend fun whoIs(context: MatrixEndpointContext<WhoIs, Unit, WhoIs.Response>): WhoIs.Response
}