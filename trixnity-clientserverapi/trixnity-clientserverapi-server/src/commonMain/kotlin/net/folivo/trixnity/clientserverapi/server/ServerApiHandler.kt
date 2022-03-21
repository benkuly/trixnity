package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

interface ServerApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(content: MatrixEndpointContext<GetVersions, Unit, GetVersions.Response>): GetVersions.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(context: MatrixEndpointContext<GetCapabilities, Unit, GetCapabilities.Response>): GetCapabilities.Response
}