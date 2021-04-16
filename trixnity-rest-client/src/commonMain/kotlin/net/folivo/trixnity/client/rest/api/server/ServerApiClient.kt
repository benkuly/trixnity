package net.folivo.trixnity.client.rest.api.server

import io.ktor.client.*
import io.ktor.client.request.*

class ServerApiClient(private val httpClient: HttpClient) {

    suspend fun getVersions(): VersionsResponse {
        return httpClient.get("/versions")
    }

    suspend fun getCapabilities(): CapabilitiesResponse {
        return httpClient.get("/r0/capabilities")
    }

}