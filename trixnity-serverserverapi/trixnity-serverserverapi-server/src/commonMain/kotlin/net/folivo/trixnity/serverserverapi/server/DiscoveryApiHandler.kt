package net.folivo.trixnity.serverserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.discovery.*

interface DiscoveryApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#getwell-knownmatrixserver">matrix spec</a>
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, GetWellKnown.Response>): GetWellKnown.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1version">matrix spec</a>
     */
    suspend fun getServerVersion(context: MatrixEndpointContext<GetServerVersion, Unit, GetServerVersion.Response>): GetServerVersion.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixkeyv2serverkeyid">matrix spec</a>
     */
    suspend fun getServerKeys(context: MatrixEndpointContext<GetServerKeys, Unit, Signed<ServerKeys, String>>): Signed<ServerKeys, String>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#post_matrixkeyv2query">matrix spec</a>
     */
    suspend fun queryServerKeys(context: MatrixEndpointContext<QueryServerKeys, QueryServerKeys.Request, QueryServerKeysResponse>): QueryServerKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixkeyv2queryservernamekeyid">matrix spec</a>
     */
    suspend fun queryKeysByServer(context: MatrixEndpointContext<QueryServerKeysByServer, Unit, QueryServerKeysResponse>): QueryServerKeysResponse
}