package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions

class ServerApiClient(private val httpClient: MatrixClientServerApiHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(): Result<GetVersions.Response> =
        httpClient.request(GetVersions)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(): Result<GetCapabilities.Response> =
        httpClient.request(GetCapabilities)
}