package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<ServerApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    serverApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @AfterTest
    fun afterTest() {
        verify(handlerMock).hasNoUnmetExpectations()
        verify(handlerMock).hasNoUnverifiedExpectations()
    }

    @Test
    fun shouldGetVersions() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getVersions)
            .whenInvokedWith(any())
            .then {
                GetVersions.Response(
                    versions = emptyList(),
                    unstable_features = mapOf()
                )
            }
        val response = client.get("/_matrix/client/versions") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {"versions":[],"unstable_features":{}}
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getVersions)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldGetCapabilities() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getCapabilities)
            .whenInvokedWith(any())
            .then {
                GetCapabilities.Response(
                    capabilities = GetCapabilities.Response.Capabilities(
                        GetCapabilities.Response.Capabilities.ChangePasswordCapability(true),
                        GetCapabilities.Response.Capabilities.RoomVersionsCapability("5", mapOf())
                    )
                )
            }
        val response = client.get("/_matrix/client/v3/capabilities") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {"capabilities":{"m.change_password":{"enabled":true},"m.room_versions":{"default":"5","available":{}}}}
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getCapabilities)
            .with(any())
            .wasInvoked()
    }
}
