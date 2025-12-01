package net.folivo.trixnity.clientserverapi.client

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.clientserverapi.client.oauth2.OAuth2MatrixClientAuthProvider
import net.folivo.trixnity.clientserverapi.client.oauth2.oAuth2
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
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MatrixClientServerApiBaseClientTest : TrixnityBaseTest() {

    private val baseUrl = Url("https://matrix.host")
    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    var onLogout: LogoutInfo? = null.also {
        scheduleSetup { onLogout = null }
    }
    private val classicAuthProviderStore =
        MatrixClientAuthProviderStore.inMemory(MatrixClientAuthProviderData.classic("access")).apply {
            scheduleSetup {
                setAuthData(MatrixClientAuthProviderData.classic("access"))
            }
        }
    private val classicAuthProvider = ClassicMatrixClientAuthProvider(classicAuthProviderStore, { onLogout = it })

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

    private val serverMetadata =
        """
            {
                "authorization_endpoint":"https://auth.matrix.host/_oauth2/authorize",
                "code_challenge_methods_supported":["S256"],
                "grant_types_supported":["refresh_token","authorization_code"],
                "issuer":"https://auth.matrix.host",
                "prompt_values_supported":["create"],
                "registration_endpoint":"https://auth.matrix.host/_oauth2/registration",
                "response_modes_supported":["query"],
                "response_types_supported":["code"],
                "revocation_endpoint":"https://auth.matrix.host/_oauth2/revoke",
                "token_endpoint":"https://auth.matrix.host/_oauth2/token"
            }
        """.trimIndent()

    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = ClassicMatrixClientAuthProvider(classicAuthProviderStore, {}),
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

        cut.request(PostPathWithDisabledAuth("1", "2"), PostPath.Request(true))
            .exceptionOrNull() shouldBe
                MatrixServerException(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse.BadJson(error = """response could not be parsed to ErrorResponse (body={"status":"ok"})"""),
                )
    }

    @Test
    fun itShouldRetryWithToken() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = ClassicMatrixClientAuthProvider(classicAuthProviderStore, {}),
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

        cut.request(PostPathWithoutAuth("1", "2"), PostPath.Request(true))
            .getOrThrow() shouldBe PostPath.Response("ok")
    }

    @Test
    fun itShouldHaveOptionalAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        classicAuthProviderStore.setAuthData(null)
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = ClassicMatrixClientAuthProvider(classicAuthProviderStore, {}),
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

        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access"))

        cut.request(PostPathWithOptionalAuth("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
    }

    @Test
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token`() = runTest {
        var refreshCalled = false
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                refreshCalled = true
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh" +
                                        "&client_id=clientId"
                                respond(
                                    """
                                    {
                                        "access_token": "access",
                                        "token_type": "Bearer",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh2"
                                    }
                                """,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )

        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe PostPath.Response("ok")
        refreshCalled shouldBe true
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token`() = runTest {
        var refreshCalled = false
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
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
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token and logout`() = runTest {
        var refreshCalled = false
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                refreshCalled = true
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh" +
                                        "&client_id=clientId"
                                respond(
                                    "{\"error\": \"invalid_grant\"}",
                                    HttpStatusCode.Unauthorized,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )

        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        shouldThrow<ClientRequestException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }.response.status shouldBe HttpStatusCode.Unauthorized
        refreshCalled shouldBe true
        onLogout shouldBe LogoutInfo(isSoft = true, isLocked = false)
    }

    @Test
    fun `Classic - should refresh token and logout`() = runTest {
        var refreshCalled = false
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled = true
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }.errorResponse.shouldBeInstanceOf<ErrorResponse.UnknownToken>()
        refreshCalled shouldBe true
        onLogout shouldBe LogoutInfo(isSoft = true, isLocked = false)
    }

    @Test
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token and not logout`() = runTest {
        var refreshCalled = false
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                refreshCalled = true
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh" +
                                        "&client_id=clientId"
                                respond(
                                    """
                                    {
                                        "access_token": "access",
                                        "token_type": "Bearer",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh2"
                                    }
                                """,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/path/1?requestParam=2" -> {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }.errorResponse.shouldBeInstanceOf<ErrorResponse.UnknownToken>()
        refreshCalled shouldBe true
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token and not logout`() = runTest {
        var refreshCalled = false
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }.errorResponse.shouldBeInstanceOf<ErrorResponse.UnknownToken>()
        refreshCalled shouldBe true
        onLogout shouldBe null
    }

    @Test
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token with old refresh token`() = runTest {
        var refreshCalled = 0
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh1"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                refreshCalled++
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh$refreshCalled" +
                                        "&client_id=clientId"
                                when (refreshCalled) {
                                    1 -> respond(
                                        """
                                    {
                                        "access_token": "access1",
                                        "token_type": "Bearer",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh2"
                                    }
                                """,
                                        HttpStatusCode.OK,
                                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                    )

                                    else -> respond(
                                        """
                                    {
                                        "access_token": "access2",
                                        "token_type": "Bearer",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh3"
                                    }
                                """,
                                        HttpStatusCode.OK,
                                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                    )
                                }
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
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
                                authHeader shouldBe "Bearer access1"
                                respond(
                                    """{"status":"ok"}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }

                        "/path/2?requestParam=2" -> {
                            val authHeader = request.headers[HttpHeaders.Authorization]
                            if (authHeader == "Bearer access1") {
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
                                authHeader shouldBe "Bearer access2"
                                respond(
                                    """{"status":"ok"}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
        refreshCalled shouldBe 1
        cut.request(PostPath("2", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
        refreshCalled shouldBe 2
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token with old refresh token`() = runTest {
        var refreshCalled = 0
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled++
                            when (refreshCalled) {
                                1 -> respond(
                                    """
                                    {
                                        "access_token": "access1",
                                        "expires_in_ms": 60000
                                    }
                                """,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )

                                else -> respond(
                                    """
                                    {
                                        "access_token": "access2",
                                        "expires_in_ms": 60000
                                    }
                                """,
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
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
                                authHeader shouldBe "Bearer access1"
                                respond(
                                    """{"status":"ok"}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }

                        "/path/2?requestParam=2" -> {
                            val authHeader = request.headers[HttpHeaders.Authorization]
                            if (authHeader == "Bearer access1") {
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
                                authHeader shouldBe "Bearer access2"
                                respond(
                                    """{"status":"ok"}""",
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }
                        }

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
        refreshCalled shouldBe 1
        cut.request(PostPath("2", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                PostPath.Response("ok")
        refreshCalled shouldBe 2
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token with parallel requests`() = runTest {
        val refreshCalled = MutableStateFlow(false)
        val continueRefresh = MutableStateFlow(false)
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        var refreshCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled.value = true
                            continueRefresh.first { it }
                            refreshCount++
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        coroutineScope {
            repeat(20) {
                launch {
                    cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                            PostPath.Response("ok")
                }
            }
            refreshCalled.first { it }
            delay(1.seconds)
            continueRefresh.value = true
        }

        refreshCalled.value shouldBe true
        refreshCount shouldBe 1
        onLogout shouldBe null
    }

    @Test
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token with parallel requests and rethrow exceptions`() = runTest {
        val refreshCalled = MutableStateFlow(false)
        val continueRefresh = MutableStateFlow(false)
        var onLogout: LogoutInfo? = null
        var refreshCount = 0
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh" +
                                        "&client_id=clientId"
                                refreshCalled.value = true
                                continueRefresh.first { it }
                                refreshCount++
                                respond(
                                    """
                                    {
                                        "error": "server_error"
                                    }
                                """,
                                    HttpStatusCode.InternalServerError,
                                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                )
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
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
                            } else fail("should never be called")
                        }

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        coroutineScope {
            repeat(20) {
                launch {
                    cut.request(PostPath("1", "2"), PostPath.Request(true))
                        .exceptionOrNull()
                        .shouldBeInstanceOf<ServerResponseException>().response.status shouldBe HttpStatusCode.InternalServerError
                }
            }
            refreshCalled.first { it }
            delay(1.seconds)
            continueRefresh.value = true
        }

        refreshCalled.value shouldBe true
        refreshCount shouldBe 20
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token with parallel requests and rethrow exceptions`() = runTest {
        val refreshCalled = MutableStateFlow(false)
        val continueRefresh = MutableStateFlow(false)
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        var refreshCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled.value = true
                            continueRefresh.first { it }
                            refreshCount++
                            respond(
                                """{
                                          "errcode": "INTERNAL_ERROR",
                                          "error": "boom"
                                        }""",
                                HttpStatusCode.InternalServerError,
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
                            } else fail("should never be called")
                        }

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        coroutineScope {
            repeat(20) {
                launch {
                    cut.request(PostPath("1", "2"), PostPath.Request(true))
                        .exceptionOrNull().shouldBeInstanceOf<MatrixServerException>()
                }
            }
            refreshCalled.first { it }
            delay(1.seconds)
            continueRefresh.value = true
        }

        refreshCalled.value shouldBe true
        refreshCount shouldBe 20
        onLogout shouldBe null
    }

    @Test
    @OptIn(MSC4191::class)
    fun `OAuth 2 - should refresh token with parallel requests and handle request abort`() = runTest {
        val refreshCalled = MutableStateFlow(false)
        val continueRefresh = MutableStateFlow(false)
        var onLogout: LogoutInfo? = null
        var refreshCount = 0
        val oAuth2AuthProvider =
            OAuth2MatrixClientAuthProvider(
                baseUrl = baseUrl,
                store = MatrixClientAuthProviderStore.inMemory(
                    MatrixClientAuthProviderData.oAuth2(
                        clientId = "clientId",
                        accessToken = "access_old",
                        accessTokenExpiresInS = null,
                        refreshToken = "refresh"
                    )
                ),
                onLogout = { onLogout = it },
                httpClientEngine = scopedMockEngine(false) {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://auth.matrix.host/_oauth2/token" -> {
                                request.body.toByteArray().decodeToString() shouldBe
                                        "grant_type=refresh_token" +
                                        "&refresh_token=refresh" +
                                        "&client_id=clientId"
                                refreshCalled.value = true
                                refreshCount += 1
                                continueRefresh.first { it }
                                if (refreshCount == 1) {
                                    delay(Duration.INFINITE)
                                    fail("should never be called")
                                } else {
                                    respond(
                                        """
                                    {
                                        "access_token": "access",
                                        "token_type": "Bearer",
                                        "expires_in_ms": 60000,
                                        "refresh_token": "refresh2"
                                    }
                                """,
                                        HttpStatusCode.OK,
                                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                                    )
                                }
                            }

                            "https://matrix.host/_matrix/client/v1/auth_metadata" -> respond(
                                serverMetadata,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )

                            else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                        }
                    }
                },
                httpClientConfig = null
            )
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = oAuth2AuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        coroutineScope {
            val firstCall = launch {
                cut.request(PostPath("1", "2"), PostPath.Request(true))
            }
            repeat(20) {
                launch {
                    shouldNotThrowAny {
                        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                                PostPath.Response("ok")
                    }
                }
            }
            refreshCalled.first { it }
            refreshCalled.value = false
            delay(1.seconds)
            firstCall.cancel()
            continueRefresh.value = true
            refreshCalled.first { it }
            delay(1.seconds)
            continueRefresh.value = true
        }

        refreshCalled.value shouldBe true
        refreshCount shouldBe 2
        onLogout shouldBe null
    }

    @Test
    fun `Classic - should refresh token with parallel requests and handle request abort`() = runTest {
        val refreshCalled = MutableStateFlow(false)
        val continueRefresh = MutableStateFlow(false)
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        var refreshCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/_matrix/client/v3/refresh" -> {
                            request.body.toByteArray().decodeToString() shouldBe """{"refresh_token":"refresh"}"""
                            refreshCalled.value = true
                            refreshCount++
                            continueRefresh.first { it }
                            if (refreshCount == 1) {
                                delay(Duration.INFINITE)
                                fail("should never be called")
                            } else {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        coroutineScope {
            val firstCall = launch {
                cut.request(PostPath("1", "2"), PostPath.Request(true))
            }
            repeat(20) {
                launch {
                    shouldNotThrowAny {
                        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe
                                PostPath.Response("ok")
                    }
                }
            }
            refreshCalled.first { it }
            refreshCalled.value = false
            delay(1.seconds)
            firstCall.cancel()
            continueRefresh.value = true
            refreshCalled.first { it }
            delay(1.seconds)
            continueRefresh.value = true
        }

        refreshCalled.value shouldBe true
        refreshCount shouldBe 2
        onLogout shouldBe null
    }

    @Test
    fun itShouldCallOnLogout() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
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
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
            json = json,
            eventContentSerializerMappings = mappings,
        )

        val error =
            cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).exceptionOrNull()
                .shouldBeInstanceOf<MatrixServerException>()
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        onLogout shouldBe LogoutInfo(true, false)
    }

    @Test
    fun uiaRequestShouldRefresh() = runTest {
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access_old", null, "refresh"))
        var refreshCalled = false
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(withDefaultResponse = false) {
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

                        else -> respond("404 NOT_FOUND ${request.url}", HttpStatusCode.NotFound)
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
        )

        cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
        refreshCalled shouldBe true
        onLogout shouldBe null
    }

    @Test
    fun uiaRequestShouldCallOnLogoutOnLock() = runTest {
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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
            json = json,
            eventContentSerializerMappings = mappings,
        )

        shouldThrow<MatrixServerException> {
            cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
        }.errorResponse.shouldBeInstanceOf<ErrorResponse.UserLocked>()
        onLogout shouldBe LogoutInfo(true, true)
    }

    @Test
    fun uiaRequestShouldAuthenticate() = runTest {
        var requestCount = 0
        classicAuthProviderStore.setAuthData(MatrixClientAuthProviderData.classic("access", null, "refresh"))
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
                addHandler { request ->
                    when (requestCount) {
                        0 -> {
                            requestCount++
                            respond(
                                """
                                {
                                  "flows":[
                                    {
                                      "stages":["we.are.so.smartly"]
                                    }
                                  ],
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
                                    "type":"we.are.so.smartly",
                                    "just_break_everything":"NOT_USED",
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

        val result = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPath.Request(true)).getOrThrow()
        result.shouldBeInstanceOf<UIA.Step<*>>()
        result.state shouldBe UIAState(
            completed = listOf(),
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Unknown("we.are.so.smartly"))),
            ),
            session = "session1"
        )
        result.authenticate(
            AuthenticationRequest.Unknown(
                JsonObject(mapOf("just_break_everything" to JsonPrimitive("NOT_USED"))),
                AuthenticationType.Unknown("we.are.so.smartly")
            )
        ).getOrThrow()
        result.getFallbackUrl(AuthenticationType.Unknown("we.are.so.smartly")).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/we.are.so.smartly/fallback/web?session=session1"
    }

    @Test
    fun uiaRequestShouldReturnStepAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
            httpClientEngine = scopedMockEngine(false) {
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
            .getOrThrow()
        result.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
    }

    @Test
    fun uiaRequestShouldReturnErrorAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixClientServerApiBaseClient(
            baseUrl = baseUrl,
            authProvider = classicAuthProvider,
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