package net.folivo.trixnity.client.api.server

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerApiClientTest {
    @Test
    fun shouldGetVersions() = runBlockingTest {
        val response = VersionsResponse(
            versions = emptyList(),
            unstable_features = mapOf()
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/versions", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            Json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.server.getVersions().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetCapabilities() = runBlockingTest {
        val response = CapabilitiesResponse(
            capabilities = CapabilitiesResponse.Capabilities(
                CapabilitiesResponse.ChangePasswordCapability(true),
                CapabilitiesResponse.RoomVersionsCapability(
                    "5",
                    mapOf()
                )
            )
        )
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/r0/capabilities", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            Json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.server.getCapabilities().getOrThrow()
        assertEquals(response, result)
    }
}