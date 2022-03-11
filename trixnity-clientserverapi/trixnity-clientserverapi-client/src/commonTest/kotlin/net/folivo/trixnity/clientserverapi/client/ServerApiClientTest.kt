package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerApiClientTest {
    @Test
    fun shouldGetVersions() = runTest {
        val response = GetVersions.Response(
            versions = emptyList(),
            unstable_features = mapOf()
        )
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/versions", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getVersions().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetCapabilities() = runTest {
        val response = GetCapabilities.Response(
            capabilities = GetCapabilities.Response.Capabilities(
                GetCapabilities.Response.Capabilities.ChangePasswordCapability(true),
                GetCapabilities.Response.Capabilities.RoomVersionsCapability("5", mapOf())
            )
        )
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/capabilities", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getCapabilities().getOrThrow()
        assertEquals(response, result)
    }
}