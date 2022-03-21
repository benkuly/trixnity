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
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.mockative.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA.Success
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

class AuthenticationRouteTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<AuthenticationApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    authenticationApiRoutes(handlerMock, json, mapping)
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
    fun shouldIsUsernameAvailable() = testApplication {
        initCut()
        val response = client.get("/_matrix/client/v3/register/available?username=user")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verify(handlerMock).suspendFunction(handlerMock::isUsernameAvailable)
            .with(matching { it.endpoint.username == "user" })
            .wasInvoked()
    }

    @Test
    fun shouldRegister() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::register)
            .whenInvokedWith(any())
            .then { Success(Register.Response(UserId("user", "server"))) }
        val response = client.post("/_matrix/client/v3/register?kind=user") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "username":"someUsername",
                      "password":"somePassword",
                      "device_id":"someDeviceId",
                      "initial_device_display_name":"someInitialDeviceDisplayName",
                      "inhibit_login":true
                    }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """{"user_id":"@user:server"}"""
        }

        verify(handlerMock).suspendFunction(handlerMock::register)
            .with(matching {
                it.endpoint.kind shouldBe AccountType.USER
                it.requestBody.request shouldBe Register.Request(
                    username = "someUsername",
                    password = "somePassword",
                    deviceId = "someDeviceId",
                    inhibitLogin = true,
                    initialDeviceDisplayName = "someInitialDeviceDisplayName"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetLoginTypes() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getLoginTypes)
            .whenInvokedWith(any())
            .then {
                GetLoginTypes.Response(
                    setOf(
                        LoginType.Unknown(
                            "m.login.sso", JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("m.login.sso"),
                                    "identity_providers" to JsonArray(
                                        listOf(
                                            JsonObject(
                                                mapOf(
                                                    "id" to JsonPrimitive("oidc-keycloak"),
                                                    "name" to JsonPrimitive("FridaysForFuture")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        LoginType.Token,
                        LoginType.Password,
                    )
                )
            }
        val response = client.get("/_matrix/client/v3/login")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "flows":[
                    {
                      "type":"m.login.sso",
                      "identity_providers":[
                        {
                          "id":"oidc-keycloak",
                          "name":"FridaysForFuture"
                        }
                      ]
                    },
                    {
                      "type":"m.login.token"
                    },
                    {
                      "type":"m.login.password"
                    }
                  ]
                }
                """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::getLoginTypes)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldLogin() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::login)
            .whenInvokedWith(any())
            .then {
                Login.Response(
                    userId = UserId("@cheeky_monkey:matrix.org"),
                    accessToken = "abc123",
                    deviceId = "GHTYAJCE",
                    discoveryInformation = Login.Response.DiscoveryInformation(
                        Login.Response.DiscoveryInformation.HomeserverInformation("https://example.org"),
                        Login.Response.DiscoveryInformation.IdentityServerInformation("https://id.example.org")
                    )
                )
            }
        val response = client.post("/_matrix/client/v3/login") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "type":"m.login.password",
                      "identifier":{
                        "user":"cheeky_monkey",
                        "type":"m.id.user"
                      },
                      "password":"ilovebananas",
                      "initial_device_display_name":"Jungle Phone"
                    }
                """.trim()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "user_id":"@cheeky_monkey:matrix.org",
                  "access_token":"abc123",
                  "device_id":"GHTYAJCE",
                  "well_known":{
                    "m.homeserver":{
                      "base_url":"https://example.org"
                    },
                    "m.identity_server":{
                      "base_url":"https://id.example.org"
                    }
                  }
                }
                """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::login)
            .with(matching {
                it.requestBody shouldBe Login.Request(
                    type = LoginType.Password.name,
                    identifier = IdentifierType.User("cheeky_monkey"),
                    password = "ilovebananas",
                    initialDeviceDisplayName = "Jungle Phone"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldLogout() = testApplication {
        initCut()
        val response = client.post("/_matrix/client/v3/logout") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verify(handlerMock).suspendFunction(handlerMock::logout)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldLogoutAll() = testApplication {
        initCut()
        val response = client.post("/_matrix/client/v3/logout/all") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verify(handlerMock).suspendFunction(handlerMock::logoutAll)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldDeactivateAccount() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deactivateAccount)
            .whenInvokedWith(any())
            .then { Success(DeactivateAccount.Response(DeactivateAccount.Response.IdServerUnbindResult.SUCCESS)) }
        val response = client.post("/_matrix/client/v3/account/deactivate") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"id_server":"id.host"}""")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """{"id_server_unbind_result":"success"}"""
        }

        verify(handlerMock).suspendFunction(handlerMock::deactivateAccount)
            .with(matching {
                it.requestBody shouldBe RequestWithUIA(DeactivateAccount.Request("id.host"), null)
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldChangePassword() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::changePassword)
            .whenInvokedWith(any())
            .then { Success(Unit) }
        val response = client.post("/_matrix/client/v3/account/password") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"new_password":"newPassword","logout_devices":false}""")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """{}"""
        }

        verify(handlerMock).suspendFunction(handlerMock::changePassword)
            .with(matching {
                it.requestBody shouldBe RequestWithUIA(ChangePassword.Request("newPassword", false), null)
                true
            })
            .wasInvoked()
    }
}