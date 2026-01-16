package de.connect2x.trixnity.serverserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.serverserverapi.model.discovery.*

interface DiscoveryApiHandler {
    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, GetWellKnown.Response>): GetWellKnown.Response

    /**
     * @see [GetServerVersion]
     */
    suspend fun getServerVersion(context: MatrixEndpointContext<GetServerVersion, Unit, GetServerVersion.Response>): GetServerVersion.Response

    /**
     * @see [GetServerKeys]
     */
    suspend fun getServerKeys(context: MatrixEndpointContext<GetServerKeys, Unit, Signed<ServerKeys, String>>): Signed<ServerKeys, String>

    /**
     * @see [QueryServerKeys]
     */
    suspend fun queryServerKeys(context: MatrixEndpointContext<QueryServerKeys, QueryServerKeys.Request, QueryServerKeysResponse>): QueryServerKeysResponse

    /**
     * @see [QueryServerKeysByServer]
     */
    suspend fun queryKeysByServer(context: MatrixEndpointContext<QueryServerKeysByServer, Unit, QueryServerKeysResponse>): QueryServerKeysResponse
}