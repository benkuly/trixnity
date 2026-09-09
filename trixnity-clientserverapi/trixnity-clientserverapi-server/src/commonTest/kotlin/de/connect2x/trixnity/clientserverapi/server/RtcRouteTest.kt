package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.clientserverapi.model.rtc.livekit.GetLiveKitToken
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.resetAnswers
import dev.mokkery.resetCalls
import dev.mokkery.verifySuspend
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@MSC4143
@MSC4195
class RtcRouteTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<RtcApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(UserId("user", "server"), "deviceId"),
                        null,
                    )
                }
            }
            matrixApiServer(json) { rtcApiRoutes(handlerMock, json, mapping) }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetTransports() = testApplication {
        initCut()
        everySuspend { handlerMock.getTransports(any()) }
            .returns(
                GetTransports.Response(listOf(RtcTransport.Unknown("dino", buildJsonObject { put("type", "dino") })))
            )
        val response = client.get("/_matrix/client/unstable/org.matrix.msc4143/rtc/transports")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe
                """
                    {
                      "transports": [
                        {
                          "type":"dino"
                        }
                      ]
                    }
                """
                    .trimToFlatJson()
        }
        verifySuspend { handlerMock.getTransports(any()) }
    }

    @Test
    fun shouldGetLiveKitToken() = testApplication {
        initCut()
        everySuspend { handlerMock.getLiveKitToken(any()) }.returns(GetLiveKitToken.Response("abc.woof.abc"))
        val response =
            client.post("/_matrix/client/unstable/io.element.msc4195/rtc/livekit/get_token") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "server_name": "matrix2.host",
                        "url": "wss://livekit.matrix2.host",
                        "room_id": "!room:matrix2.host",
                        "slot_id": "call#123",
                        "member_id": "member-123"
                    }
                    """
                        .trimIndent()
                )
            }

        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """{"jwt": "abc.woof.abc"}""".trimToFlatJson()
        }
        verifySuspend { handlerMock.getLiveKitToken(any()) }
    }

    @Test
    fun shouldDelegateDelayedLeave() = testApplication {
        initCut()
        everySuspend { handlerMock.delegateDelayedLeave(any()) }.returns(Unit)
        val response =
            client.post("/_matrix/client/v1/rtc/livekit/delegate_delayed_leave") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "url": "wss://livekit.matrix2.host",
                        "room_id": "!room:matrix2.host",
                        "slot_id": "call#123",
                        "member_id": "member-123",
                        "delay_id": "123"
                    }
                    """
                        .trimIndent()
                )
            }

        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json
            this.body<String>() shouldBe """{}""".trimToFlatJson()
        }
        verifySuspend { handlerMock.delegateDelayedLeave(any()) }
    }
}
