package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationApiClientTest {

    @Test
    fun shouldIsUsernameAvailable() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/register/available?username=user", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.isUsernameAvailable("user").getOrThrow()
    }

    @Test
    fun shouldRegister() = runTest {
        val response = Register.Response(UserId("user", "server"))
        val expectedRequest = """
            {
              "username":"someUsername",
              "password":"somePassword",
              "device_id":"someDeviceId",
              "initial_device_display_name":"someInitialDeviceDisplayName",
              "inhibit_login":true
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            })
        val result = matrixRestClient.authentication.register(
            username = "someUsername",
            password = "somePassword",
            accountType = AccountType.USER,
            deviceId = "someDeviceId",
            initialDeviceDisplayName = "someInitialDeviceDisplayName",
            inhibitLogin = true
        ).getOrThrow()
        require(result is UIA.Success)
        assertEquals(response, result.value)
    }

    @Test
    fun shouldGetLoginTypes() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            })
        val result = matrixRestClient.authentication.getLoginTypes().getOrThrow()
        assertEquals(
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
            ), result
        )
    }

    @Test
    fun shouldLogin() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/login", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "type":"m.login.password",
                              "identifier":{
                                "user":"cheeky_monkey",
                                "type":"m.id.user"
                              },
                              "password":"ilovebananas",
                              "initial_device_display_name":"Jungle Phone"
                            }
                        """.trimToFlatJson()
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
            })
        val result = matrixRestClient.authentication.login(
            type = LoginType.Password,
            identifier = IdentifierType.User("cheeky_monkey"),
            passwordOrToken = "ilovebananas",
            initialDeviceDisplayName = "Jungle Phone"
        ).getOrThrow()
        assertEquals(
            Login.Response(
                userId = UserId("@cheeky_monkey:matrix.org"),
                accessToken = "abc123",
                deviceId = "GHTYAJCE",
                discoveryInformation = Login.Response.DiscoveryInformation(
                    Login.Response.DiscoveryInformation.HomeserverInformation("https://example.org"),
                    Login.Response.DiscoveryInformation.IdentityServerInformation("https://id.example.org")
                )
            ), result
        )
    }

    @Test
    fun shouldLogout() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/logout", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.logout()
    }

    @Test
    fun shouldLogoutAll() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/logout/all", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.logoutAll()
    }

    @Test
    fun shouldDeactivateAccount() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/deactivate", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{"id_server":"id.host"}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """{"id_server_unbind_result": "success"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.authentication.deactivateAccount("id.host").getOrThrow()
            .shouldBeInstanceOf<UIA.Success<DeactivateAccount.Response>>()
        result.value shouldBe DeactivateAccount.Response(DeactivateAccount.Response.IdServerUnbindResult.SUCCESS)
    }

    @Test
    fun shouldChangePassword() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            })
        val result = matrixRestClient.authentication.changePassword("newPassword").getOrThrow()
        assertTrue { result is UIA.Success }
    }
}