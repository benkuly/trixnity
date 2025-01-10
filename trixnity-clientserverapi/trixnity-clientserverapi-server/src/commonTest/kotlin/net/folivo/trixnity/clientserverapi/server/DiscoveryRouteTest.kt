package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetSupport
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.BeforeTest
import kotlin.test.Test

class DiscoveryRouteTest {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<DiscoveryApiHandler>()

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

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetWellKnown() = testApplication {
        initCut()
        everySuspend { handlerMock.getWellKnown(any()) }
            .returns(
                DiscoveryInformation(
                    homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
                    identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
                )
            )
        val response = client.get("/.well-known/matrix/client")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
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
        verifySuspend {
            handlerMock.getWellKnown(any())
        }
    }

    @Test
    fun shouldGetSupport() = testApplication {
        initCut()
        everySuspend { handlerMock.getSupport(any()) }
            .returns(
                GetSupport.Response(
                    contacts = listOf(
                        GetSupport.Response.Contact(
                            emailAddress = "admin@example.org",
                            userId = UserId("@admin:example.org"),
                            role = GetSupport.Response.Contact.Role.Admin,
                        ),
                        GetSupport.Response.Contact(
                            emailAddress = "dino@example.org",
                            role = GetSupport.Response.Contact.Role.Unknown("m.role.dino"),
                        )
                    ),
                    supportPage = "https://example.org/support.html"
                )
            )
        val response = client.get("/.well-known/matrix/support")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "contacts": [
                        {
                          "email_address": "admin@example.org",
                          "matrix_id": "@admin:example.org",
                          "role": "m.role.admin"
                        },
                        {
                          "email_address": "dino@example.org",
                          "role": "m.role.dino"
                        }
                      ],
                      "support_page": "https://example.org/support.html"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getSupport(any())
        }
    }
}