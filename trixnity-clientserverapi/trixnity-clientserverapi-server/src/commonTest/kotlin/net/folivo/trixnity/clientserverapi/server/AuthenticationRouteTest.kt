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
import io.ktor.utils.io.charsets.Charsets.UTF_8
import io.mockative.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.authentication.ThirdPartyIdentifier.Medium
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
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
    fun shouldGetWhoami() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::whoAmI)
            .whenInvokedWith(any())
            .then {
                WhoAmI.Response(UserId("user", "server"), "ABCDEF", false)
            }
        val response = client.get("/_matrix/client/v3/account/whoami") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"user_id":"@user:server","device_id":"ABCDEF","is_guest":false}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::whoAmI)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldGetWellKnown() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getWellKnown)
            .whenInvokedWith(any())
            .then {
                DiscoveryInformation(
                    homeserver = DiscoveryInformation.HomeserverInformation("https://matrix.example.com"),
                    identityServer = DiscoveryInformation.IdentityServerInformation("https://identity.example.com")
                )
            }
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

        verify(handlerMock).suspendFunction(handlerMock::getWellKnown)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldIsRegistrationTokenValid() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::isRegistrationTokenValid)
            .whenInvokedWith(any())
            .then {
                IsRegistrationTokenValid.Response(true)
            }
        val response = client.get("/_matrix/client/v1/register/m.login.registration_token/validity?token=token")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                          "valid": true
                    }
                """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::isRegistrationTokenValid)
            .with(matching {
                it.endpoint.token shouldBe "token"
                true
            })
            .wasInvoked()
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
    fun shouldGetEmailRequestTokenForPassword() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getEmailRequestTokenForPassword)
            .whenInvokedWith(any())
            .then {
                GetEmailRequestTokenForPassword.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            }
        val response = client.post("/_matrix/client/v3/account/password/email/requestToken") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "client_secret": "monkeys_are_GREAT",
                      "email": "foo@example.com",
                      "id_server": "id.example.com",
                      "next_link": "https://example.org/congratulations.html",
                      "send_attempt": 1
                    }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "sid": "123abc",
                  "submit_url": "https://example.org/path/to/submitToken"
                }
            """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::getEmailRequestTokenForPassword)
            .with(matching {
                it.requestBody shouldBe GetEmailRequestTokenForPassword.Request(
                    clientSecret = "monkeys_are_GREAT",
                    email = "foo@example.com",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    sendAttempt = 1
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetEmailRequestTokenForRegistration() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getEmailRequestTokenForRegistration)
            .whenInvokedWith(any())
            .then {
                GetEmailRequestTokenForRegistration.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            }
        val response = client.post("/_matrix/client/v3/register/email/requestToken") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "client_secret": "monkeys_are_GREAT",
                      "email": "foo@example.com",
                      "id_server": "id.example.com",
                      "next_link": "https://example.org/congratulations.html",
                      "send_attempt": 1
                    }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "sid": "123abc",
                  "submit_url": "https://example.org/path/to/submitToken"
                }
            """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::getEmailRequestTokenForRegistration)
            .with(matching {
                it.requestBody shouldBe GetEmailRequestTokenForRegistration.Request(
                    clientSecret = "monkeys_are_GREAT",
                    email = "foo@example.com",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    sendAttempt = 1
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetMsisdnRequestTokenForPassword() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getMsisdnRequestTokenForPassword)
            .whenInvokedWith(any())
            .then {
                GetMsisdnRequestTokenForPassword.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            }
        val response = client.post("/_matrix/client/v3/account/password/msisdn/requestToken") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "client_secret": "monkeys_are_GREAT",
                      "country": "GB",
                      "id_server": "id.example.com",
                      "next_link": "https://example.org/congratulations.html",
                      "phone_number": "07700900001",
                      "send_attempt": 1
                    }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "sid": "123abc",
                  "submit_url": "https://example.org/path/to/submitToken"
                }
            """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::getMsisdnRequestTokenForPassword)
            .with(matching {
                it.requestBody shouldBe GetMsisdnRequestTokenForPassword.Request(
                    clientSecret = "monkeys_are_GREAT",
                    country = "GB",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    phoneNumber = "07700900001",
                    sendAttempt = 1
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetMsisdnRequestTokenForRegistration() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getMsisdnRequestTokenForRegistration)
            .whenInvokedWith(any())
            .then {
                GetMsisdnRequestTokenForRegistration.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            }
        val response = client.post("/_matrix/client/v3/register/msisdn/requestToken") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "client_secret": "monkeys_are_GREAT",
                      "country": "GB",
                      "id_server": "id.example.com",
                      "next_link": "https://example.org/congratulations.html",
                      "phone_number": "07700900001",
                      "send_attempt": 1
                    }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "sid": "123abc",
                  "submit_url": "https://example.org/path/to/submitToken"
                }
            """.trimToFlatJson()
        }

        verify(handlerMock).suspendFunction(handlerMock::getMsisdnRequestTokenForRegistration)
            .with(matching {
                it.requestBody shouldBe GetMsisdnRequestTokenForRegistration.Request(
                    clientSecret = "monkeys_are_GREAT",
                    country = "GB",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    phoneNumber = "07700900001",
                    sendAttempt = 1
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldRegister() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::register)
            .whenInvokedWith(any())
            .then { ResponseWithUIA.Success(Register.Response(UserId("user", "server"))) }
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
                    discoveryInformation = DiscoveryInformation(
                        DiscoveryInformation.HomeserverInformation("https://example.org"),
                        DiscoveryInformation.IdentityServerInformation("https://id.example.org")
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
            .then { ResponseWithUIA.Success(DeactivateAccount.Response(IdServerUnbindResult.SUCCESS)) }
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
            .then { ResponseWithUIA.Success(Unit) }
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

    @Test
    fun shouldGetThirdPartyIdentifiers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getThirdPartyIdentifiers)
            .whenInvokedWith(any())
            .then {
                GetThirdPartyIdentifiers.Response(
                    setOf(
                        ThirdPartyIdentifier(
                            addedAt = 1535336848756,
                            address = "monkey@banana.island",
                            medium = Medium.EMAIL,
                            validatedAt = 1535176800000
                        )
                    )
                )
            }
        val response = client.get("/_matrix/client/v3/account/3pid") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getThirdPartyIdentifiers)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldAddThirdPartyIdentifiers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::addThirdPartyIdentifiers)
            .whenInvokedWith(any())
            .then { ResponseWithUIA.Success(Unit) }
        val response = client.post("/_matrix/client/v3/account/3pid/add") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_secret": "d0nt-T3ll",
                  "sid": "abc123987"
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::addThirdPartyIdentifiers)
            .with(matching {
                it.requestBody.request shouldBe AddThirdPartyIdentifiers.Request("d0nt-T3ll", "abc123987")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldBindThirdPartyIdentifiers() = testApplication {
        initCut()
        val response = client.post("/_matrix/client/v3/account/3pid/bind") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "client_secret": "d0nt-T3ll",
                  "id_access_token": "abc123_OpaqueString",
                  "id_server": "example.org",
                  "sid": "abc123987"
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::bindThirdPartyIdentifiers)
            .with(matching {
                it.requestBody shouldBe BindThirdPartyIdentifiers.Request(
                    clientSecret = "d0nt-T3ll",
                    idAccessToken = "abc123_OpaqueString",
                    idServer = "example.org",
                    sessionId = "abc123987"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteThirdPartyIdentifiers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteThirdPartyIdentifiers)
            .whenInvokedWith(any())
            .then { DeleteThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS) }
        val response = client.post("/_matrix/client/v3/account/3pid/delete") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "address": "example@example.org",
                  "id_server": "example.org",
                  "medium": "email"
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """{"id_server_unbind_result":"success"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteThirdPartyIdentifiers)
            .with(matching {
                it.requestBody shouldBe DeleteThirdPartyIdentifiers.Request(
                    address = "example@example.org",
                    idServer = "example.org",
                    medium = Medium.EMAIL
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldUnbindThirdPartyIdentifiers() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::unbindThirdPartyIdentifiers)
            .whenInvokedWith(any())
            .then { UnbindThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS) }
        val response = client.post("/_matrix/client/v3/account/3pid/unbind") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "address": "example@example.org",
                  "id_server": "example.org",
                  "medium": "email"
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """{"id_server_unbind_result":"success"}"""
        }
        verify(handlerMock).suspendFunction(handlerMock::unbindThirdPartyIdentifiers)
            .with(matching {
                it.requestBody shouldBe UnbindThirdPartyIdentifiers.Request(
                    address = "example@example.org",
                    idServer = "example.org",
                    medium = Medium.EMAIL
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetOIDCRequestToken() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getOIDCRequestToken)
            .whenInvokedWith(any())
            .then {
                GetOIDCRequestToken.Response(
                    accessToken = "SomeT0kenHere",
                    expiresIn = 3600,
                    matrixServerName = "example.com",
                )
            }
        val response =
            client.get("/_matrix/client/v3/user/%40user%3Aserver/openid/request_token") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                 "access_token": "SomeT0kenHere",
                 "expires_in": 3600,
                 "matrix_server_name": "example.com",
                 "token_type": "Bearer"
               }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getOIDCRequestToken)
            .with(matching {
                it.endpoint.userId shouldBe UserId("user", "server")
                true
            })
            .wasInvoked()
    }
}