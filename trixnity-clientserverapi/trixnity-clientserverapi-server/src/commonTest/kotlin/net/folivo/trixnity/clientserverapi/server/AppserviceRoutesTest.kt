package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.appservice.Ping
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class AppserviceRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: AppserviceApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                appserviceApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @Test
    fun shouldPing() = testApplication {
        initCut()
        everySuspending { handlerMock.ping(isAny()) }
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
        verifyWithSuspend {
            handlerMock.ping(
                assert {
                    it.endpoint.appserviceId shouldBe "appId"
                    it.requestBody shouldBe Ping.Request("1")
                }
            )
        }
    }
}