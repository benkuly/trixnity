package net.folivo.trixnity.client.rest.api.server

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.rest.MatrixRestClient
import net.folivo.trixnity.client.rest.MatrixRestClientProperties
import net.folivo.trixnity.client.rest.MatrixRestClientProperties.HomeServerProperties
import net.folivo.trixnity.client.rest.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerApiClientTest {
    @Test
    fun shouldGetVersions() = runBlockingTest {
        val response = VersionsResponse(
            versions = emptyList(),
            unstable_features = mapOf()
        )
        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
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
        val result = matrixRestClient.server.getVersions()
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
        val matrixRestClient = MatrixRestClient(
            properties = MatrixRestClientProperties(HomeServerProperties("matrix.host"), "token"),
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
        val result = matrixRestClient.server.getCapabilities()
        assertEquals(response, result)
    }
}