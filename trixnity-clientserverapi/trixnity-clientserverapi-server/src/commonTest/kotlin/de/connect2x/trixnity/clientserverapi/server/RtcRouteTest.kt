package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.core.MSC4143
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
}
