package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class DiscoveryRouteTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: DiscoveryApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                discoveryApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @Test
    fun shouldGetWellKnown() = testApplication {
        initCut()
        everySuspending { handlerMock.getWellKnown(isAny()) }
            .returns(
                DiscoveryInformation(
                    homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
                    identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
                )
            )
        val response = client.get("/.well-known/matrix/client")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "m.homeserver": {
                        "base_url": "https://matrix.example.com"
                      },
                      "m.identity_server": {
                        "base_url": "https://identity.example.com"
                      }
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getWellKnown(isAny())
        }
    }
}