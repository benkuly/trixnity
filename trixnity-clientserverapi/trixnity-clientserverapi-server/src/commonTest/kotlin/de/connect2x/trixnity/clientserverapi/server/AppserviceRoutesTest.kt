package de.connect2x.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.appservice.Ping
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AppserviceRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

    val handlerMock = mock<AppserviceApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            matrixApiServer(json) {
                appserviceApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldPing() = testApplication {
        initCut()
        everySuspend { handlerMock.ping(any()) }
            .returns(Ping.Response(1234))
        val response = client.post("/_matrix/client/v1/appservice/appId/ping") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"transaction_id":"1"}""")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"duration_ms":1234}"""
        }
        verifySuspend {
            handlerMock.ping(
                assert {
                    it.endpoint.appserviceId shouldBe "appId"
                    it.requestBody shouldBe Ping.Request("1")
                }
            )
        }
    }
}