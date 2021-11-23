package net.folivo.trixnity.client.api.server

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import net.folivo.trixnity.client.api.MatrixHttpClient

class ServerApiClient(private val httpClient: MatrixHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(): VersionsResponse {
        return httpClient.request {
            method = Get
            url("/_matrix/client/versions")
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(): CapabilitiesResponse {
        return httpClient.request {
            method = Get
            url("/_matrix/client/v3/capabilities")
        }
    }

}