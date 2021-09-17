package net.folivo.trixnity.client.api.server

import io.ktor.client.*
import io.ktor.client.request.*

class ServerApiClient(private val httpClient: HttpClient) {

    suspend fun getVersions(): VersionsResponse {
        return httpClient.get("/_matrix/client/versions")
    }

    suspend fun getCapabilities(): CapabilitiesResponse {
        return httpClient.get("/_matrix/client/r0/capabilities")
    }

}