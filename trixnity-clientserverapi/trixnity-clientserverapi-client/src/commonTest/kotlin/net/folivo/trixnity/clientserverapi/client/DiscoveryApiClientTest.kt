package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryApiClientTest {
    @Test
    fun shouldGetWellKnown() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/client", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.example.com"
                              },
                              "m.identity_server": {
                                "base_url": "https://identity.example.com"
                              },
                              "org.example.custom.property": {
                                "app_url": "https://custom.app.example.org"
                              }
                            }

                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getWellKnown().getOrThrow() shouldBe DiscoveryInformation(
            homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
            identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
        )
    }

    @Test
    fun shouldGetWellKnownRegardlessOfContentType() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/client", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "m.homeserver": {
                                "base_url": "https://matrix.example.com"
                              },
                              "m.identity_server": {
                                "base_url": "https://identity.example.com"
                              },
                              "org.example.custom.property": {
                                "app_url": "https://custom.app.example.org"
                              }
                            }

                        """.trimIndent(),
                        HttpStatusCode.OK,
                    )
                }
            })
        matrixRestClient.discovery.getWellKnown().getOrThrow() shouldBe DiscoveryInformation(
            homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
            identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
        )
    }
}