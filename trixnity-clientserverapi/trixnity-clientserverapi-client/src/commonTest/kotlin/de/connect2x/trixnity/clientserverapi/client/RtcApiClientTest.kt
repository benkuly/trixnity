package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotId
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

    @Test
    @MSC4195
    fun shouldGetLiveKitToken() = runTest {
        val matrixRestClient =
            MatrixClientServerApiClientImpl(
                baseUrl = Url("https://matrix.host"),
                httpClientEngine =
                    scopedMockEngine {
                        addHandler { request ->
                            assertEquals(
                                "/_matrix/client/unstable/io.element.msc4195/rtc/livekit/get_token",
                                request.url.fullPath,
                            )
                            assertEquals(HttpMethod.Post, request.method)
                            request.body.toByteArray().decodeToString() shouldBe
                                """
                                {
                                    "server_name": "matrix2.host",
                                    "url": "wss://livekit.matrix2.host",
                                    "room_id": "!room:matrix2.host",
                                    "slot_id": "call#123",
                                    "member_id": "member-123"
                                }
                            """
                                    .trimToFlatJson()

                            respond(
                                """
                                {
                                    "jwt": "abc.abc.abc"
                                }
                                """
                                    .trimIndent()
                            )
                        }
                    },
            )
        matrixRestClient.rtc
            .getLiveKitToken(
                "matrix2.host",
                Url("wss://livekit.matrix2.host"),
                RoomId("!room:matrix2.host"),
                RtcSlotId("call", "123"),
                "member-123",
            )
            .getOrThrow() shouldBe "abc.abc.abc"
    }

    @Test
    @MSC4195
    fun shouldDelegateDelayedLeave() = runTest {
        val matrixRestClient =
            MatrixClientServerApiClientImpl(
                baseUrl = Url("https://matrix.host"),
                httpClientEngine =
                    scopedMockEngine {
                        addHandler { request ->
                            assertEquals(
                                "/_matrix/client/v1/rtc/livekit/delegate_delayed_leave",
                                request.url.fullPath,
                            )
                            assertEquals(HttpMethod.Post, request.method)
                            request.body.toByteArray().decodeToString() shouldBe
                                """
                                {
                                    "url": "wss://livekit.matrix2.host",
                                    "room_id": "!room:matrix2.host",
                                    "slot_id": "call#123",
                                    "member_id": "member-123",
                                    "delay_id": "123"
                                }
                            """
                                    .trimToFlatJson()

                            respond(
                                """
                                {
                                    "jwt": "abc.abc.abc"
                                }
                                """
                                    .trimIndent()
                            )
                        }
                    },
            )
        matrixRestClient.rtc
            .delegateDelayedLeave(
                Url("wss://livekit.matrix2.host"),
                RoomId("!room:matrix2.host"),
                RtcSlotId("call", "123"),
                RtcMemberId("member-123"),
                "123"
            )
            .getOrThrow()
    }
}
