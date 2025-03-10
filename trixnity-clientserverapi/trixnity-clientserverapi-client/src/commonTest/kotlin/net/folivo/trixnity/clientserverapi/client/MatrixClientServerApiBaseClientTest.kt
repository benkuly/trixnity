package net.folivo.trixnity.clientserverapi.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationType
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.clientserverapi.model.uia.UIAState
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixClientServerApiBaseClientTest {

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    private val authProvider = MatrixAuthProvider.classicInMemory("access")

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    data class PostPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPath.Request, PostPath.Response> {
        @Serializable
        data class Request(
            val includeDino: Boolean
        )

        @Serializable
        data class Response(
            val status: String
        )
    }

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    @Auth(AuthRequired.NO)
    data class PostPathWithoutAuth(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPath.Request, PostPath.Response>

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    @Auth(AuthRequired.OPTIONAL)
    data class PostPathWithOptionalAuth(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPath.Request, PostPath.Response>

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    @Auth(AuthRequired.NEVER)
    data class PostPathWithDisabledAuth(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPath.Request, PostPath.Response>

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    @Auth(AuthRequired.YES)
    data class PostPathWithUIA(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixUIAEndpoint<PostPath.Request, PostPath.Response>

    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.fullPath shouldBe "/path/1?requestParam=2"
                    request.url.host shouldBe "matrix.host"
                    request.headers[HttpHeaders.Authorization] shouldBe "Bearer access"
                    request.headers[HttpHeaders.Accept] shouldBe Application.Json.toString()
                    request.method shouldBe Post
                    request.body.toByteArray().decodeToString() shouldBe """{"includeDino":true}"""
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
    }

    @Test
    fun itShouldNotHaveAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe null
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPathWithoutAuth("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
    }

    @Test
    fun itShouldNotRetryWithTokenOnNever() = runTest {
        val testTokenStore = ClassicMatrixAuthProvider.BearerTokensStore.InMemory()

        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = MatrixAuthProvider.classic(testTokenStore),
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe null
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        testTokenStore.bearerTokens = ClassicMatrixAuthProvider.BearerTokens("access", null)

        cut.request(PostPathWithDisabledAuth("1", "2"), PostPath.Request(true))
            .exceptionOrNull() shouldBe
                MatrixServerException(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse.CustomErrorResponse("UNKNOWN", error = """{"status":"ok"}""")
                )
    }

    @Test
    fun itShouldRetryWithToken() = runTest {
        val testTokenStore = ClassicMatrixAuthProvider.BearerTokensStore.InMemory()

        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = MatrixAuthProvider.classic(testTokenStore),
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    if (request.headers[HttpHeaders.Authorization] == "Bearer access") {
                        respond(
                            """{"status":"ok"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    } else {
                        respond(
                            """{"status":"not ok"}""",
                            HttpStatusCode.Unauthorized,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        testTokenStore.bearerTokens = ClassicMatrixAuthProvider.BearerTokens("access", null)

        cut.request(PostPathWithoutAuth("1", "2"), PostPath.Request(true))
            .getOrThrow() shouldBe PostPath.Response("ok")
    }

    @Test
    fun itShouldHaveOptionalAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val testTokenStore = ClassicMatrixAuthProvider.BearerTokensStore.InMemory()

        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = MatrixAuthProvider.classic(testTokenStore),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe null
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
                addHandler { request ->
                    request.headers[HttpHeaders.Authorization] shouldBe "Bearer access"
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPathWithOptionalAuth("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")

        testTokenStore.bearerTokens = ClassicMatrixAuthProvider.BearerTokens("access", null)

        cut.request(PostPathWithOptionalAuth("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
    }

    @Test
    fun itShouldRefreshToken() = runTest {
        var refreshCalled = false
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = MatrixAuthProvider.classicInMemory(
                accessToken = "access_old",
                refreshToken = "refresh",
            ),
            onLogout = { onLogout = it },
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled = true
                            respond(
                                """
                                    {
                                        "access_token": "access",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh2"
                                    }
                                """,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        "/path/1?requestParam=2" -> {
                            val authHeader = request.headers[HttpHeaders.Authorization]
                            if (authHeader == "Bearer access_old") {
                                respond(
                                    """
                                        {
                                          "errcode": "M_UNKNOWN_TOKEN",
                                          "error": "Soft logged out (access token expired)",
                                          "soft_logout": true
                                        }
                                    """.trimIndent(),
                                    HttpStatusCode.Unauthorized,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            } else {
                                authHeader shouldBe "Bearer access"
                                respond(
                                    """{"status":"ok"}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }

                        else -> respond("404 NOT_FOUND", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
        refreshCalled shouldBe true
        onLogout shouldBe null
    }

    @Test
    fun itShouldCallOnLogout() = runTest {
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{
                            "errcode": "M_UNKNOWN_TOKEN",
                            "error": "Only unicorns accepted",
                            "soft_logout": true
                       }""".trimIndent(),
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            onLogout = { onLogout = it },
            json = json,
            eventContentSerializerMappings = mappings,
        )
        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        error.statusCode shouldBe HttpStatusCode.Unauthorized
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        error.errorResponse.error shouldBe "Only unicorns accepted"
        onLogout shouldBe LogoutInfo(isSoft = true, isLocked = false)
    }

    @Test
    fun itShouldCallOnLogoutOnLocked() = runTest {
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{
                            "errcode": "M_USER_LOCKED",
                            "error": "you are blocked",
                            "soft_logout": true
                       }""".trimIndent(),
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            onLogout = { onLogout = it },
            json = json,
            eventContentSerializerMappings = mappings,
        )
        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        error.statusCode shouldBe HttpStatusCode.Unauthorized
        assertEquals(
            ErrorResponse.UserLocked::class,
            error.errorResponse::class
        )
        error.errorResponse.error shouldBe "you are blocked"
        onLogout shouldBe LogoutInfo(isSoft = true, isLocked = true)
    }

    @Test
    fun itShouldCallOnLogoutWhenRefreshingToken() = runTest {
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = MatrixAuthProvider.classicInMemory(
                accessToken = "access_old",
                refreshToken = "refresh",
            ),
            onLogout = { onLogout = it },
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            respond(
                                """
                                    {
                                        "errcode": "M_UNKNOWN_TOKEN",
                                        "error": "Only unicorns accepted",
                                        "soft_logout": true
                                    }
                                """,
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        "/path/1?requestParam=2" -> {
                            request.headers[HttpHeaders.Authorization] shouldBe "Bearer access_old"
                            respond(
                                """
                                        {
                                          "errcode": "M_UNKNOWN_TOKEN",
                                          "error": "Soft logged out (access token expired)",
                                          "soft_logout": true
                                        }
                                    """.trimIndent(),
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> respond("404 NOT_FOUND", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        error.statusCode shouldBe HttpStatusCode.Unauthorized
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        error.errorResponse.error shouldBe "Only unicorns accepted"
        onLogout shouldBe LogoutInfo(isSoft = true, isLocked = false)
    }

    @Test
    fun uiaRequestShouldReturnSuccess() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.fullPath shouldBe "/path/1?requestParam=2"
                    request.url.host shouldBe "matrix.host"
                    request.headers[HttpHeaders.Authorization] shouldBe "Bearer access"
                    request.headers[HttpHeaders.Accept] shouldBe Application.Json.toString()
                    request.method shouldBe Post
                    request.body.toByteArray().decodeToString() shouldBe """{"includeDino":true}"""
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true))
            .getOrThrow() shouldBe UIA.Success(PostPath.Response("ok"))
    }

    @Test
    fun uiaRequestShouldReturnError() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{"errcode": "M_NOT_FOUND", "error": "not found"}""",
                        HttpStatusCode.NotFound,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val error = shouldThrow<MatrixServerException> {
            cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        error.statusCode shouldBe HttpStatusCode.NotFound
        assertEquals(
            ErrorResponse.NotFound::class,
            error.errorResponse::class
        )
    }

    @Test
    fun uiaRequestShouldCallOnLogout() = runTest {
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{ 
                                "errcode": "M_UNKNOWN_TOKEN",
                                "error": "Only unicorns accepted",
                                "soft_logout": true
                            }""".trimIndent(),
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            onLogout = { onLogout = it },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val error = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
            .shouldBeInstanceOf<UIA.Error<*>>()
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        onLogout shouldBe LogoutInfo(true, false)
    }

    @Test
    fun uiaRequestShouldCallOnLogoutOnLock() = runTest {
        var onLogout: LogoutInfo? = null
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{ 
                                "errcode": "M_USER_LOCKED",
                                "error": "your are locked",
                                "soft_logout": true
                            }""".trimIndent(),
                        HttpStatusCode.Unauthorized,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            onLogout = { onLogout = it },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val error = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
            .shouldBeInstanceOf<UIA.Error<*>>()
        assertEquals(
            ErrorResponse.UserLocked::class,
            error.errorResponse::class
        )
        onLogout shouldBe LogoutInfo(true, true)
    }

    @Test
    fun uiaRequestShouldReturnStepAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    when (requestCount) {
                        0 -> {
                            requestCount++
                            respond(
                                """
                                {
                                  "flows":[
                                    {
                                      "stages":["m.login.password"]
                                    },
                                    {
                                      "stages":["m.login.sso","m.login.recaptcha"]
                                    }
                                  ],
                                  "params": {
                                      "example.type.baz": {
                                          "example_key": "foobar"
                                      },
                                      "m.login.terms": {
                                          "policies": {
                                              "terms_of_service": {
                                                  "version": "1.2",
                                                  "en": {
                                                      "name": "Terms of Service",
                                                      "url": "https://example.org/somewhere/terms-1.2-en.html"
                                                  },
                                                  "fr": {
                                                      "name": "Conditions d'utilisation",
                                                      "url": "https://example.org/somewhere/terms-1.2-fr.html"
                                                  }
                                              }
                                          }
                                      }
                                  },
                                  "session":"session1"
                                }
                            """.trimIndent(),
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            requestCount++
                            request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "help":"me",
                                  "auth":{
                                    "identifier":{
                                      "user":"username",
                                      "type":"m.id.user"                                     
                                    },
                                    "password":"password",
                                    "session":"session1",
                                    "type":"m.login.password"
                                  }
                                }
                                """.trimToFlatJson()
                            respond(
                                """{"status":"ok"}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val result = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
        result.shouldBeInstanceOf<UIA.Step<*>>()
        result.state shouldBe UIAState(
            completed = listOf(),
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Password)),
                UIAState.FlowInformation(listOf(AuthenticationType.SSO, AuthenticationType.Recaptcha)),
            ),
            parameter = mapOf(
                AuthenticationType.Unknown("example.type.baz") to UIAState.Parameter.Unknown(
                    buildJsonObject {
                        put("example_key", JsonPrimitive("foobar"))
                    }
                ),
                AuthenticationType.TermsOfService to UIAState.Parameter.TermsOfService(
                    mapOf(
                        "terms_of_service" to UIAState.Parameter.TermsOfService.PolicyDefinition(
                            "1.2", mapOf(
                                "en" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                    "Terms of Service", "https://example.org/somewhere/terms-1.2-en.html"
                                ),
                                "fr" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                    "Conditions d'utilisation", "https://example.org/somewhere/terms-1.2-fr.html"
                                ),
                            )
                        )
                    )
                )
            ),
            session = "session1"
        )
        result.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
        result.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
    }

    @Test
    fun uiaRequestShouldReturnErrorAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = Url("https://matrix.host"),
            authProvider = authProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (requestCount) {
                        0 -> {
                            requestCount++
                            request.body.toByteArray().decodeToString() shouldBe """
                                        {
                                          "includeDino":true
                                        }
                                        """.trimToFlatJson()
                            respond(
                                """
                                            {
                                              "errcode": "M_NOT_FOUND",
                                              "error":"",
                                              "flows":[
                                                {
                                                  "stages":["m.login.password"]
                                                },
                                                {
                                                  "stages":["m.login.sso","m.login.recaptcha"]
                                                }
                                              ],
                                              "params": {
                                                  "example.type.baz": {
                                                      "example_key": "foobar"
                                                  },
                                                  "m.login.terms": {
                                                      "policies": {
                                                          "terms_of_service": {
                                                              "version": "1.2",
                                                              "en": {
                                                                  "name": "Terms of Service",
                                                                  "url": "https://example.org/somewhere/terms-1.2-en.html"
                                                              },
                                                              "fr": {
                                                                  "name": "Conditions d'utilisation",
                                                                  "url": "https://example.org/somewhere/terms-1.2-fr.html"
                                                              }
                                                          }
                                                      }
                                                  }
                                              },
                                              "session":"session1"
                                            }
                                            """.trimIndent(),
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        1 -> {
                            requestCount++
                            request.body.toByteArray().decodeToString() shouldBe """
                                        {
                                          "includeDino":true,
                                          "auth":{
                                            "type":"m.login.password",
                                            "identifier":{
                                              "user":"username",
                                              "type":"m.id.user"                                     
                                            },
                                            "password":"password",
                                            "session":"session1"
                                          }
                                        }
                                        """.trimToFlatJson()
                            respond(
                                """
                                            {
                                              "errcode": "M_NOT_FOUND",
                                              "error":"",
                                              "flows":[
                                                {
                                                  "stages":["m.login.password"]
                                                },
                                                {
                                                  "stages":["m.login.sso","m.login.recaptcha"]
                                                }
                                              ],
                                              "params": {
                                                  "example.type.baz": {
                                                      "example_key": "foobar"
                                                  },
                                                  "m.login.terms": {
                                                      "policies": {
                                                          "terms_of_service": {
                                                              "version": "1.2",
                                                              "en": {
                                                                  "name": "Terms of Service",
                                                                  "url": "https://example.org/somewhere/terms-1.2-en.html"
                                                              },
                                                              "fr": {
                                                                  "name": "Conditions d'utilisation",
                                                                  "url": "https://example.org/somewhere/terms-1.2-fr.html"
                                                              }
                                                          }
                                                      }
                                                  }
                                              },
                                              "session":"session1"
                                            }
                                            """.trimIndent(),
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }

                        else -> {
                            requestCount++
                            request.body.toByteArray().decodeToString() shouldBe """
                                        {
                                          "includeDino":true,
                                          "auth":{
                                            "type":"m.login.password",
                                            "identifier":{
                                              "user":"username",
                                              "type":"m.id.user"                                     
                                            },
                                            "password":"password",
                                            "session":"session1"
                                          }
                                        }
                                        """.trimToFlatJson()
                            respond(
                                """{"status":"ok"}""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val expectedUIAState = UIAState(
            completed = listOf(),
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Password)),
                UIAState.FlowInformation(listOf(AuthenticationType.SSO, AuthenticationType.Recaptcha)),
            ),
            parameter = mapOf(
                AuthenticationType.Unknown("example.type.baz") to UIAState.Parameter.Unknown(
                    buildJsonObject {
                        put("example_key", JsonPrimitive("foobar"))
                    }
                ),
                AuthenticationType.TermsOfService to UIAState.Parameter.TermsOfService(
                    mapOf(
                        "terms_of_service" to UIAState.Parameter.TermsOfService.PolicyDefinition(
                            "1.2", mapOf(
                                "en" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                    "Terms of Service", "https://example.org/somewhere/terms-1.2-en.html"
                                ),
                                "fr" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                    "Conditions d'utilisation", "https://example.org/somewhere/terms-1.2-fr.html"
                                ),
                            )
                        )
                    )
                )
            ),
            session = "session1"
        )
        val result1 = cut.uiaRequest(
            PostPathWithUIA("1", "2"),
            PostPath.Request(true)
        ).getOrThrow()
        result1.shouldBeInstanceOf<UIA.Error<*>>()
        result1.state shouldBe expectedUIAState
        result1.errorResponse shouldBe ErrorResponse.NotFound("")
        result1.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
        val result2 = result1.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
            .getOrThrow()
        result2.shouldBeInstanceOf<UIA.Error<*>>()
        result2.state shouldBe expectedUIAState
        result2.errorResponse shouldBe ErrorResponse.NotFound("")
        result2.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
        result2.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
            .getOrThrow()
        requestCount shouldBe 3
    }
}