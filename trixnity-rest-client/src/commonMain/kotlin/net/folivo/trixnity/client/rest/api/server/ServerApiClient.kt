package net.folivo.matrix.restclient.api.server

import io.ktor.client.*
import io.ktor.client.request.*
import net.folivo.trixnity.client.rest.api.server.CapabilitiesResponse
import net.folivo.trixnity.client.rest.api.server.VersionsResponse

class ServerApiClient(private val httpClient: HttpClient) {

    suspend fun getVersions(): VersionsResponse {
        return httpClient.get("/versions")
    }

    suspend fun getCapabilities(): CapabilitiesResponse {
        return httpClient.get("/r0/capabilities")
    }

}