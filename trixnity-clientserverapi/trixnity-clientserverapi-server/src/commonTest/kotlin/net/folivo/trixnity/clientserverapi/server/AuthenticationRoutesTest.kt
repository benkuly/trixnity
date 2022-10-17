package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
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
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class AuthenticationRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: AuthenticationApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                authenticationApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @Test
    fun shouldGetWhoami() = testApplication {
        initCut()
        everySuspending { handlerMock.whoAmI(isAny()) }
            .returns(WhoAmI.Response(UserId("user", "server"), "ABCDEF", false))
        val response = client.get("/_matrix/client/v3/account/whoami") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """{"user_id":"@user:server","device_id":"ABCDEF","is_guest":false}"""
        }
        verifyWithSuspend {
            handlerMock.whoAmI(isAny())
        }
    }

    @Test
    fun shouldIsRegistrationTokenValid() = testApplication {
        initCut()
        everySuspending { handlerMock.isRegistrationTokenValid(isAny()) }
            .returns(IsRegistrationTokenValid.Response(true))
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

        verifyWithSuspend {
            handlerMock.isRegistrationTokenValid(assert { it.endpoint.token shouldBe "token" })
        }
    }

    @Test
    fun shouldIsUsernameAvailable() = testApplication {
        initCut()
        everySuspending { handlerMock.isUsernameAvailable(isAny()) }
            .returns(Unit)
        val response = client.get("/_matrix/client/v3/register/available?username=user")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verifyWithSuspend {
            handlerMock.isUsernameAvailable(assert { it.endpoint.username shouldBe "user" })
        }
    }

    @Test
    fun shouldGetEmailRequestTokenForPassword() = testApplication {
        initCut()
        everySuspending { handlerMock.getEmailRequestTokenForPassword(isAny()) }
            .returns(
                GetEmailRequestTokenForPassword.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            )
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

        verifyWithSuspend {
            handlerMock.getEmailRequestTokenForPassword(assert {
                it.requestBody shouldBe GetEmailRequestTokenForPassword.Request(
                    clientSecret = "monkeys_are_GREAT",
                    email = "foo@example.com",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    sendAttempt = 1
                )
            })
        }
    }

    @Test
    fun shouldGetEmailRequestTokenForRegistration() = testApplication {
        initCut()
        everySuspending { handlerMock.getEmailRequestTokenForRegistration(isAny()) }
            .returns(
                GetEmailRequestTokenForRegistration.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            )
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

        verifyWithSuspend {
            handlerMock.getEmailRequestTokenForRegistration(assert {
                it.requestBody shouldBe GetEmailRequestTokenForRegistration.Request(
                    clientSecret = "monkeys_are_GREAT",
                    email = "foo@example.com",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    sendAttempt = 1
                )
            })
        }
    }

    @Test
    fun shouldGetMsisdnRequestTokenForPassword() = testApplication {
        initCut()
        everySuspending { handlerMock.getMsisdnRequestTokenForPassword(isAny()) }
            .returns(
                GetMsisdnRequestTokenForPassword.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            )
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

        verifyWithSuspend {
            handlerMock.getMsisdnRequestTokenForPassword(assert {
                it.requestBody shouldBe GetMsisdnRequestTokenForPassword.Request(
                    clientSecret = "monkeys_are_GREAT",
                    country = "GB",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    phoneNumber = "07700900001",
                    sendAttempt = 1
                )
            })
        }
    }

    @Test
    fun shouldGetMsisdnRequestTokenForRegistration() = testApplication {
        initCut()
        everySuspending { handlerMock.getMsisdnRequestTokenForRegistration(isAny()) }
            .returns(
                GetMsisdnRequestTokenForRegistration.Response(
                    sessionId = "123abc",
                    submitUrl = "https://example.org/path/to/submitToken"
                )
            )
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

        verifyWithSuspend {
            handlerMock.getMsisdnRequestTokenForRegistration(assert {
                it.requestBody shouldBe GetMsisdnRequestTokenForRegistration.Request(
                    clientSecret = "monkeys_are_GREAT",
                    country = "GB",
                    idServer = "id.example.com",
                    nextLink = "https://example.org/congratulations.html",
                    phoneNumber = "07700900001",
                    sendAttempt = 1
                )
            })
        }
    }

    @Test
    fun shouldRegister() = testApplication {
        initCut()
        everySuspending { handlerMock.register(isAny()) }
            .returns(ResponseWithUIA.Success(Register.Response(UserId("user", "server"))))
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

        verifyWithSuspend {
            handlerMock.register(assert {
                it.endpoint.kind shouldBe AccountType.USER
                it.requestBody.request shouldBe Register.Request(
                    username = "someUsername",
                    password = "somePassword",
                    deviceId = "someDeviceId",
                    inhibitLogin = true,
                    initialDeviceDisplayName = "someInitialDeviceDisplayName"
                )
            })
        }
    }

    @Test
    fun shouldGetLoginTypes() = testApplication {
        initCut()
        everySuspending { handlerMock.getLoginTypes(isAny()) }
            .returns(
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
            )
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
        verifyWithSuspend {
            handlerMock.getLoginTypes(isAny())
        }
    }

    @Test
    fun shouldLogin() = testApplication {
        initCut()
        everySuspending { handlerMock.login(isAny()) }
            .returns(
                Login.Response(
                    userId = UserId("@cheeky_monkey:matrix.org"),
                    accessToken = "abc123",
                    deviceId = "GHTYAJCE",
                    discoveryInformation = DiscoveryInformation(
                        DiscoveryInformation.HomeserverInformation("https://example.org"),
                        DiscoveryInformation.IdentityServerInformation("https://id.example.org")
                    )
                )
            )
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

        verifyWithSuspend {
            handlerMock.login(assert {
                it.requestBody shouldBe Login.Request(
                    type = LoginType.Password.name,
                    identifier = IdentifierType.User("cheeky_monkey"),
                    password = "ilovebananas",
                    initialDeviceDisplayName = "Jungle Phone"
                )
            })
        }
    }

    @Test
    fun shouldLogout() = testApplication {
        initCut()
        everySuspending { handlerMock.logout(isAny()) }
            .returns(Unit)
        val response = client.post("/_matrix/client/v3/logout") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verifyWithSuspend {
            handlerMock.logout(isAny())
        }
    }

    @Test
    fun shouldLogoutAll() = testApplication {
        initCut()
        everySuspending { handlerMock.logoutAll(isAny()) }
            .returns(Unit)
        val response = client.post("/_matrix/client/v3/logout/all") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }

        verifyWithSuspend {
            handlerMock.logoutAll(isAny())
        }
    }

    @Test
    fun shouldDeactivateAccount() = testApplication {
        initCut()
        everySuspending { handlerMock.deactivateAccount(isAny()) }
            .returns(ResponseWithUIA.Success(DeactivateAccount.Response(IdServerUnbindResult.SUCCESS)))
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

        verifyWithSuspend {
            handlerMock.deactivateAccount(assert {
                it.requestBody shouldBe RequestWithUIA(DeactivateAccount.Request("id.host"), null)
            })
        }
    }

    @Test
    fun shouldChangePassword() = testApplication {
        initCut()
        everySuspending { handlerMock.changePassword(isAny()) }
            .returns(ResponseWithUIA.Success(Unit))
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

        verifyWithSuspend {
            handlerMock.changePassword(assert {
                it.requestBody shouldBe RequestWithUIA(ChangePassword.Request("newPassword", false), null)
            })
        }
    }

    @Test
    fun shouldGetThirdPartyIdentifiers() = testApplication {
        initCut()
        everySuspending { handlerMock.getThirdPartyIdentifiers(isAny()) }
            .returns(
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
            )
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

        verifyWithSuspend {
            handlerMock.getThirdPartyIdentifiers(isAny())
        }
    }

    @Test
    fun shouldAddThirdPartyIdentifiers() = testApplication {
        initCut()
        everySuspending { handlerMock.addThirdPartyIdentifiers(isAny()) }
            .returns(ResponseWithUIA.Success(Unit))
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

        verifyWithSuspend {
            handlerMock.addThirdPartyIdentifiers(assert {
                it.requestBody.request shouldBe AddThirdPartyIdentifiers.Request("d0nt-T3ll", "abc123987")
            })
        }
    }

    @Test
    fun shouldBindThirdPartyIdentifiers() = testApplication {
        initCut()
        everySuspending { handlerMock.bindThirdPartyIdentifiers(isAny()) }
            .returns(Unit)
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

        verifyWithSuspend {
            handlerMock.bindThirdPartyIdentifiers(assert {
                it.requestBody shouldBe BindThirdPartyIdentifiers.Request(
                    clientSecret = "d0nt-T3ll",
                    idAccessToken = "abc123_OpaqueString",
                    idServer = "example.org",
                    sessionId = "abc123987"
                )
            })
        }
    }

    @Test
    fun shouldDeleteThirdPartyIdentifiers() = testApplication {
        initCut()
        everySuspending { handlerMock.deleteThirdPartyIdentifiers(isAny()) }
            .returns(DeleteThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS))
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

        verifyWithSuspend {
            handlerMock.deleteThirdPartyIdentifiers(assert {
                it.requestBody shouldBe DeleteThirdPartyIdentifiers.Request(
                    address = "example@example.org",
                    idServer = "example.org",
                    medium = Medium.EMAIL
                )
            })
        }
    }

    @Test
    fun shouldUnbindThirdPartyIdentifiers() = testApplication {
        initCut()
        everySuspending { handlerMock.unbindThirdPartyIdentifiers(isAny()) }
            .returns(UnbindThirdPartyIdentifiers.Response(IdServerUnbindResult.SUCCESS))
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

        verifyWithSuspend {
            handlerMock.unbindThirdPartyIdentifiers(assert {
                it.requestBody shouldBe UnbindThirdPartyIdentifiers.Request(
                    address = "example@example.org",
                    idServer = "example.org",
                    medium = Medium.EMAIL
                )
            })
        }
    }

    @Test
    fun shouldGetOIDCRequestToken() = testApplication {
        initCut()
        everySuspending { handlerMock.getOIDCRequestToken(isAny()) }
            .returns(
                GetOIDCRequestToken.Response(
                    accessToken = "SomeT0kenHere",
                    expiresIn = 3600,
                    matrixServerName = "example.com",
                )
            )
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

        verifyWithSuspend {
            handlerMock.getOIDCRequestToken(assert {
                it.endpoint.userId shouldBe UserId("user", "server")
            })
        }
    }

    @Test
    fun shouldRefresh() = testApplication {
        initCut()
        everySuspending { handlerMock.refresh(isAny()) }
            .returns(
                Refresh.Response(
                    accessToken = "a_new_token",
                    accessTokenExpiresInMs = 60_000,
                    refreshToken = "another_new_token"
                )
            )
        val response = client.post("/_matrix/client/v3/refresh") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "refresh_token":"some_token"
                    }
                """.trim()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "access_token":"a_new_token",
                  "expires_in_ms":60000,
                  "refresh_token":"another_new_token"
                }
                """.trimToFlatJson()
        }

        verifyWithSuspend {
            handlerMock.refresh(assert {
                it.requestBody shouldBe Refresh.Request(
                    refreshToken = "some_token"
                )
            })
        }
    }
}