package net.folivo.trixnity.serverserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.discovery.*

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