package net.folivo.trixnity.client.api.authentication

import io.kotest.assertions.json.shouldEqualJson
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.LoginResponse.DiscoveryInformation
import net.folivo.trixnity.client.api.authentication.LoginResponse.DiscoveryInformation.HomeserverInformation
import net.folivo.trixnity.client.api.authentication.LoginResponse.DiscoveryInformation.IdentityServerInformation
import net.folivo.trixnity.client.api.runBlockingTest
import net.folivo.trixnity.client.api.uia.UIA
import net.folivo.trixnity.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticationApiClientTest {

    @Test
    fun shouldIsUsernameAvailable() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/register/available?username=user", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.authentication.isUsernameAvailable("user")
    }

    @Test
    fun shouldRegister() = runBlockingTest {
        val response = RegisterResponse(UserId("user", "server"))
        val expectedRequest = """
            {
              "username":"someUsername",
              "password":"somePassword",
              "device_id":"someDeviceId",
              "initial_device_display_name":"someInitialDeviceDisplayName",
              "inhibit_login":true
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/register?kind=user", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                        respond(
                            Json.encodeToString(response),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.authentication.register(
            username = "someUsername",
            password = "somePassword",
            accountType = AccountType.USER,
            deviceId = "someDeviceId",
            initialDeviceDisplayName = "someInitialDeviceDisplayName",
            inhibitLogin = true
        )
        require(result is UIA.UIASuccess)
        assertEquals(response, result.value)
    }

    @Test
    fun shouldGetLoginTypes() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/login", request.url.fullPath)
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """
                                {
                                  "flows": [
                                    {
                                      "type": "m.login.sso",
                                      "identity_providers": [
                                        {
                                          "id": "oidc-keycloak",
                                          "name": "FridaysForFuture"
                                        }
                                      ]
                                    },
                                    {
                                      "type": "m.login.token"
                                    },
                                    {
                                      "type": "m.login.password"
                                    }
                                  ]
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.authentication.getLoginTypes()
        assertEquals(
            setOf(
                LoginType.Unknown("m.login.sso"),
                LoginType.Token,
                LoginType.Password,
            ), result
        )
    }

    @Test
    fun shouldLogin() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/login", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                            {
                              "type": "m.login.password",
                              "identifier": {
                                "type": "m.id.user",
                                "user": "cheeky_monkey"
                              },
                              "password": "ilovebananas",
                              "initial_device_display_name": "Jungle Phone"
                            }
                        """.trimIndent()
                        )
                        respond(
                            """
                                {
                                  "user_id": "@cheeky_monkey:matrix.org",
                                  "access_token": "abc123",
                                  "device_id": "GHTYAJCE",
                                  "well_known": {
                                    "m.homeserver": {
                                      "base_url": "https://example.org"
                                    },
                                    "m.identity_server": {
                                      "base_url": "https://id.example.org"
                                    }
                                  }
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.authentication.login(
            type = LoginType.Password,
            identifier = IdentifierType.User("cheeky_monkey"),
            passwordOrToken = "ilovebananas",
            initialDeviceDisplayName = "Jungle Phone"
        )
        assertEquals(
            LoginResponse(
                userId = UserId("@cheeky_monkey:matrix.org"),
                accessToken = "abc123",
                deviceId = "GHTYAJCE",
                discoveryInformation = DiscoveryInformation(
                    HomeserverInformation("https://example.org"), IdentityServerInformation("https://id.example.org")
                )
            ), result
        )
    }

    @Test
    fun shouldLogout() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/logout", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.authentication.logout()
    }

    @Test
    fun shouldLogoutAll() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/logout/all", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        matrixRestClient.authentication.logoutAll()
    }

    @Test
    fun shouldDeactivateAccount() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/account/deactivate", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """{"id_server":"id.host"}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.authentication.deactivateAccount("id.host")
        assertTrue { result is UIA.UIASuccess }
    }

    @Test
    fun shouldChangePassword() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/client/v3/account/password", request.url.fullPath)
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals(
                            """{"new_password":"newPassword","logout_devices":false}""",
                            request.body.toByteArray().decodeToString()
                        )
                        respond(
                            "{}",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            })
        val result = matrixRestClient.authentication.changePassword("newPassword")
        assertTrue { result is UIA.UIASuccess }
    }
}