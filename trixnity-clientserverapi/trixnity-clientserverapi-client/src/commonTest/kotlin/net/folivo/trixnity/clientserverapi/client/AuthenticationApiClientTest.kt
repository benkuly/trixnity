package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.authentication.ThirdPartyIdentifier.Medium
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticationApiClientTest {

    @Test
    fun shouldGetWhoami() = runTest {
        val response = WhoAmI.Response(UserId("user", "server"), "ABCDEF", false)
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/whoami", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.authentication.whoAmI().getOrThrow()
        assertEquals(WhoAmI.Response(UserId("user", "server"), "ABCDEF", false), result)
    }

    @Test
    fun shouldIsRegistrationTokenValid() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/register/m.login.registration_token/validity?token=token",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "valid": true
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.isRegistrationTokenValid("token").getOrThrow() shouldBe true
    }

    @Test
    fun shouldIsUsernameAvailable() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/register/available?username=user", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "available": true
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.isUsernameAvailable("user").getOrThrow() shouldBe true
    }

    @Test
    fun shouldGetEmailRequestTokenForPassword() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/password/email/requestToken", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                        {
                          "client_secret": "monkeys_are_GREAT",
                          "email": "foo@example.com",
                          "id_server": "id.example.com",
                          "next_link": "https://example.org/congratulations.html",
                          "send_attempt": 1
                        }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "sid": "123abc",
                              "submit_url": "https://example.org/path/to/submitToken"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getEmailRequestTokenForPassword(
            GetEmailRequestTokenForPassword.Request(
                clientSecret = "monkeys_are_GREAT",
                email = "foo@example.com",
                idServer = "id.example.com",
                nextLink = "https://example.org/congratulations.html",
                sendAttempt = 1
            )
        ).getOrThrow() shouldBe GetEmailRequestTokenForPassword.Response(
            sessionId = "123abc",
            submitUrl = "https://example.org/path/to/submitToken"
        )
    }

    @Test
    fun shouldGetEmailRequestTokenForRegistration() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/register/email/requestToken", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                        {
                          "client_secret": "monkeys_are_GREAT",
                          "email": "foo@example.com",
                          "id_server": "id.example.com",
                          "next_link": "https://example.org/congratulations.html",
                          "send_attempt": 1
                        }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "sid": "123abc",
                              "submit_url": "https://example.org/path/to/submitToken"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getEmailRequestTokenForRegistration(
            GetEmailRequestTokenForRegistration.Request(
                clientSecret = "monkeys_are_GREAT",
                email = "foo@example.com",
                idServer = "id.example.com",
                nextLink = "https://example.org/congratulations.html",
                sendAttempt = 1
            )
        ).getOrThrow() shouldBe GetEmailRequestTokenForRegistration.Response(
            sessionId = "123abc",
            submitUrl = "https://example.org/path/to/submitToken"
        )
    }

    @Test
    fun shouldGetMsisdnRequestTokenForPassword() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/password/msisdn/requestToken", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                        {
                          "client_secret": "monkeys_are_GREAT",
                          "country": "GB",
                          "id_server": "id.example.com",
                          "next_link": "https://example.org/congratulations.html",
                          "phone_number": "07700900001",
                          "send_attempt": 1
                        }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "sid": "123abc",
                              "submit_url": "https://example.org/path/to/submitToken"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getMsisdnRequestTokenForPassword(
            GetMsisdnRequestTokenForPassword.Request(
                clientSecret = "monkeys_are_GREAT",
                country = "GB",
                idServer = "id.example.com",
                nextLink = "https://example.org/congratulations.html",
                phoneNumber = "07700900001",
                sendAttempt = 1
            )
        ).getOrThrow() shouldBe GetMsisdnRequestTokenForPassword.Response(
            sessionId = "123abc",
            submitUrl = "https://example.org/path/to/submitToken"
        )
    }

    @Test
    fun shouldGetMsisdnRequestTokenForRegistration() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/register/msisdn/requestToken", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                        {
                          "client_secret": "monkeys_are_GREAT",
                          "country": "GB",
                          "id_server": "id.example.com",
                          "next_link": "https://example.org/congratulations.html",
                          "phone_number": "07700900001",
                          "send_attempt": 1
                        }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "sid": "123abc",
                              "submit_url": "https://example.org/path/to/submitToken"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getMsisdnRequestTokenForRegistration(
            GetMsisdnRequestTokenForRegistration.Request(
                clientSecret = "monkeys_are_GREAT",
                country = "GB",
                idServer = "id.example.com",
                nextLink = "https://example.org/congratulations.html",
                phoneNumber = "07700900001",
                sendAttempt = 1
            )
        ).getOrThrow() shouldBe GetMsisdnRequestTokenForRegistration.Response(
            sessionId = "123abc",
            submitUrl = "https://example.org/path/to/submitToken"
        )
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
                LoginType.SSO(
                    setOf(
                        LoginType.SSO.IdentityProvider(
                            id = "oidc-keycloak",
                            name = "FridaysForFuture",
                        )
                    )
                ),
                LoginType.Token(),
                LoginType.Password,
            ), result
        )
    }

    @Test
    fun shouldLogin() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
            password = "ilovebananas",
            initialDeviceDisplayName = "Jungle Phone"
        ).getOrThrow()
        assertEquals(
            Login.Response(
                userId = UserId("@cheeky_monkey:matrix.org"),
                accessToken = "abc123",
                deviceId = "GHTYAJCE",
                discoveryInformation = DiscoveryInformation(
                    DiscoveryInformation.HomeserverInformation("https://example.org"),
                    DiscoveryInformation.IdentityServerInformation("https://id.example.org")
                )
            ), result
        )
    }

    @Test
    fun shouldLogout() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/deactivate", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{"id_server":"id.host","erase":false}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """{"id_server_unbind_result": "success"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.authentication.deactivateAccount("id.host", false).getOrThrow()
            .shouldBeInstanceOf<UIA.Success<DeactivateAccount.Response>>()
        result.value shouldBe DeactivateAccount.Response(IdServerUnbindResult.SUCCESS)
    }

    @Test
    fun shouldChangePassword() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
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

    @Test
    fun shouldGetThirdPartyIdentifiers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/3pid", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "threepids": [
                                {
                                  "added_at": 1535336848756,
                                  "address": "monkey@banana.island",
                                  "medium": "email",
                                  "validated_at": 1535176800000
                                }
                              ]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getThirdPartyIdentifiers().getOrThrow() shouldBe setOf(
            ThirdPartyIdentifier(
                addedAt = 1535336848756,
                address = "monkey@banana.island",
                medium = Medium.EMAIL,
                validatedAt = 1535176800000
            )
        )
    }

    @Test
    fun shouldAddThirdPartyIdentifiers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/3pid/add", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "client_secret": "d0nt-T3ll",
                              "sid": "abc123987"
                            }
                    """.trimToFlatJson()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.addThirdPartyIdentifiers("d0nt-T3ll", "abc123987")
            .getOrThrow() shouldBe UIA.Success(Unit)
    }

    @Test
    fun shouldBindThirdPartyIdentifiers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/3pid/bind", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "client_secret": "d0nt-T3ll",
                              "sid": "abc123987",
                              "id_access_token": "abc123_OpaqueString",
                              "id_server": "example.org"
                            }
                    """.trimToFlatJson()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.bindThirdPartyIdentifiers(
            "d0nt-T3ll",
            "abc123987",
            "abc123_OpaqueString",
            "example.org"
        ).getOrThrow()
    }

    @Test
    fun shouldDeleteThirdPartyIdentifiers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/3pid/delete", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "address": "example@example.org",
                              "id_server": "example.org",
                              "medium": "email"
                            }
                    """.trimToFlatJson()
                    respond(
                        """{"id_server_unbind_result":"success"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.deleteThirdPartyIdentifiers("example@example.org", "example.org", Medium.EMAIL)
            .getOrThrow() shouldBe DeleteThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS)
    }

    @Test
    fun shouldUnbindThirdPartyIdentifiers() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/account/3pid/unbind", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "address": "example@example.org",
                              "id_server": "example.org",
                              "medium": "email"
                            }
                    """.trimToFlatJson()
                    respond(
                        """{"id_server_unbind_result":"success"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.unbindThirdPartyIdentifiers("example@example.org", "example.org", Medium.EMAIL)
            .getOrThrow() shouldBe UnbindThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS)
    }

    @Test
    fun shouldGetOIDCRequestToken() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/user/@user:server/openid/request_token", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        """
                           {
                             "access_token": "SomeT0kenHere",
                             "expires_in": 3600,
                             "matrix_server_name": "example.com",
                             "token_type": "Bearer"
                           }
                       """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.authentication.getOIDCRequestToken(UserId("user", "server"))
            .getOrThrow() shouldBe GetOIDCRequestToken.Response(
            accessToken = "SomeT0kenHere",
            expiresIn = 3600,
            matrixServerName = "example.com",
        )
    }

    @Test
    fun shouldRefresh() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/refresh", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "refresh_token":"some_token"
                            }
                        """.trimToFlatJson()
                    respond(
                        """
                            {
                              "access_token":"a_new_token",
                              "expires_in_ms":60000,
                              "refresh_token":"another_new_token"
                            }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.authentication.refresh("some_token").getOrThrow()
        assertEquals(
            Refresh.Response(
                accessToken = "a_new_token",
                accessTokenExpiresInMs = 60_000,
                refreshToken = "another_new_token"
            ), result
        )
    }

    @Test
    fun shouldGetToken() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/login/get_token", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """{}""",
                        request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """{
                                  "expires_in_ms": 120000,
                                  "login_token": "<opaque string>"
                                }
                                """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.authentication.getToken().getOrThrow()
            .shouldBeInstanceOf<UIA.Success<GetToken.Response>>()
        result.value shouldBe GetToken.Response(
            loginToken = "<opaque string>",
            expiresInMs = 120000
        )
    }
}