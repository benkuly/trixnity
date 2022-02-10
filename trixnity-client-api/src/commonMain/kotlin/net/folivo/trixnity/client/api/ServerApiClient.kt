package net.folivo.trixnity.client.api

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.client.api.model.server.CapabilitiesResponse
import net.folivo.trixnity.client.api.model.server.VersionsResponse

class ServerApiClient(private val httpClient: MatrixHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(): Result<VersionsResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/versions")
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(): Result<CapabilitiesResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/capabilities")
        }

}