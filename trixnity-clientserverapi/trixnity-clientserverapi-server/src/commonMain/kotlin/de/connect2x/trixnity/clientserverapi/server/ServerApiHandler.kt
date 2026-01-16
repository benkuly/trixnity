package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import de.connect2x.trixnity.clientserverapi.model.server.Search
import de.connect2x.trixnity.clientserverapi.model.server.WhoIs

interface ServerApiHandler {
    /**
     * @see [GetVersions]
     */
    suspend fun getVersions(content: MatrixEndpointContext<GetVersions, Unit, GetVersions.Response>): GetVersions.Response

    /**
     * @see [GetCapabilities]
     */
    suspend fun getCapabilities(context: MatrixEndpointContext<GetCapabilities, Unit, GetCapabilities.Response>): GetCapabilities.Response

    /**
     * @see [Search]
     */
    suspend fun search(context: MatrixEndpointContext<Search, Search.Request, Search.Response>): Search.Response

    /**
     * @see [WhoIs]
     */
    suspend fun whoIs(context: MatrixEndpointContext<WhoIs, Unit, WhoIs.Response>): WhoIs.Response
}