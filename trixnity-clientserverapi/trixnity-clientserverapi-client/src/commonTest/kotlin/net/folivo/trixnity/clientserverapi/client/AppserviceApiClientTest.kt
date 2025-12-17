package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.appservice.Ping
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class AppserviceApiClientTest : TrixnityBaseTest() {

    @Test
    fun shouldPing() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/appservice/appId/ping", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """{"transaction_id":"1"}"""
                    respond(
                        """
                            {
                              "duration_ms": 1234
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.appservice.ping("appId", "1").getOrThrow() shouldBe Ping.Response(1234)
    }
}