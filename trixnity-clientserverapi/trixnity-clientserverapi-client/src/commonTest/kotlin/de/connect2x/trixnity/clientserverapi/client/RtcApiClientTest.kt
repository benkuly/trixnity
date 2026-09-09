package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.testutils.scopedMockEngine
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@MSC4143
class RtcApiClientTest : TrixnityBaseTest() {
    @Test
    fun shouldGetTransports() = runTest {
        val matrixRestClient =
            MatrixClientServerApiClientImpl(
                baseUrl = Url("https://matrix.host"),
                httpClientEngine =
                    scopedMockEngine {
                        addHandler { request ->
                            assertEquals(
                                "/_matrix/client/unstable/org.matrix.msc4143/rtc/transports",
                                request.url.fullPath,
                            )
                            assertEquals(HttpMethod.Get, request.method)
                            respond(
                                """
                                {
                                  "transports": [
                                    {
                                      "type":"dino"
                                    }
                                  ]
                                }
                                """
                                    .trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString()),
                            )
                        }
                    },
            )
        matrixRestClient.rtc.getTransports().getOrThrow() shouldBe
            listOf(RtcTransport.Unknown("dino", buildJsonObject { put("type", "dino") }))
    }
}
