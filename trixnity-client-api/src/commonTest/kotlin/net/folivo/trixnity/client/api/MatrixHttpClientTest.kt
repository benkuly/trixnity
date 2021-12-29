package net.folivo.trixnity.client.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Post
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.model.authentication.IdentifierType
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequest
import net.folivo.trixnity.client.api.model.uia.AuthenticationType
import net.folivo.trixnity.client.api.model.uia.UIAState
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixHttpClientTest {

    private val json = createMatrixJson()

    @Serializable
    data class OkResponse(
        val status: String = "default"
    )

    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/path?param=dino", request.url.fullPath)
                        assertEquals("matrix.host", request.url.host)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                        assertEquals(Post, request.method)
                        assertEquals("""{"help":"me"}""", request.body.toByteArray().decodeToString())
                        respond(
                            """{"status":"ok"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        cut.request<OkResponse> {
            method = Post
            url("/path")
            parameter("param", "dino")
            body = mapOf("help" to "me")
        }.getOrThrow() shouldBe OkResponse("ok")
    }

    @Test
    fun itShouldCatchNotOkResponseAndThrowMatrixServerException() = runTest {
        try {
            val cut = MatrixHttpClient(
                baseUrl = Url("https://matrix.host"),
                initialHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            respond(
                                """{
                            "errcode": "M_FORBIDDEN",
                            "error": "Only unicorns accepted"
                       }""".trimIndent(),
                                HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                },
                json = json,
                accessToken = MutableStateFlow("token")
            )
            cut.request<OkResponse> {
                method = Post
                url("/path")
            }.getOrThrow()
            fail("should throw ${MatrixServerException::class.simpleName}")
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals(ErrorResponse.Forbidden::class, error.errorResponse::class)
            assertEquals("Only unicorns accepted", error.errorResponse.error)
        }
    }

    @Test
    fun itShouldCatchAllOtherNotOkResponseAndThrowMatrixServerException() = runTest {
        try {
            val cut = MatrixHttpClient(
                baseUrl = Url("https://matrix.host"),
                initialHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            respond(
                                "NO_UNICORN",
                                HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                },
                json = json,
                accessToken = MutableStateFlow("token")
            )
            cut.request<OkResponse> {
                method = Post
                url("/path")
            }.getOrThrow()
            fail("should throw ${MatrixServerException::class.simpleName}")
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals(ErrorResponse.CustomErrorResponse::class, error.errorResponse::class)
            assertEquals("NO_UNICORN", error.errorResponse.error)
        }
    }

    @Test
    fun uiaRequestShouldPreventUsageOfBodyInBuilder() = runTest {
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/path?param=dino", request.url.fullPath)
                        assertEquals("matrix.host", request.url.host)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                        assertEquals(Post, request.method)
                        assertEquals("""{"help":"me"}""", request.body.toByteArray().decodeToString())
                        respond(
                            """{"status":"ok"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        shouldThrow<IllegalArgumentException> {
            cut.uiaRequest<OkResponse> {
                method = Post
                url("/path")
                parameter("param", "dino")
                body = mapOf("help" to "me")
            }.getOrThrow()
        }
    }

    @Test
    fun uiaRequestShouldReturnSuccess() = runTest {
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/path?param=dino", request.url.fullPath)
                        assertEquals("matrix.host", request.url.host)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                        assertEquals(Post, request.method)
                        assertEquals("""{"help":"me"}""", request.body.toByteArray().decodeToString())
                        respond(
                            """{"status":"ok"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        cut.uiaRequest<Map<String, String>, OkResponse>(
            body = mapOf("help" to "me")
        ) {
            method = Post
            url("/path")
            parameter("param", "dino")
        }.getOrThrow() shouldBe UIA.UIASuccess(OkResponse("ok"))
    }

    @Test
    fun uiaRequestShouldReturnError() = runTest {
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            """{"errcode": "M_NOT_FOUND"}""",
                            HttpStatusCode.NotFound,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        try {
            cut.uiaRequest<Map<String, String>, OkResponse>(
                body = mapOf("help" to "me")
            ) {
                method = Post
                url("/path")
                parameter("param", "dino")
            }.getOrThrow()
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals(ErrorResponse.NotFound::class, error.errorResponse::class)
        }
    }

    @Test
    fun uiaRequestShouldReturnStepAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
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
                                  "params":{
                                      "example.type.baz":{
                                          "example_key":"foobar"
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
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        val result = cut.uiaRequest<Map<String, String>, OkResponse>(
            body = mapOf("help" to "me")
        ) {
            method = Post
            url("/path")
            parameter("param", "dino")
        }.getOrThrow()
        result.shouldBeInstanceOf<UIA.UIAStep<*>>()
        result.state shouldBe UIAState(
            completed = listOf(),
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Password)),
                UIAState.FlowInformation(listOf(AuthenticationType.SSO, AuthenticationType.Recaptcha)),
            ),
            parameter = JsonObject(
                mapOf(
                    "example.type.baz" to JsonObject(
                        mapOf(
                            "example_key" to JsonPrimitive("foobar")
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
        val cut = MatrixHttpClient(
            baseUrl = Url("https://matrix.host"),
            initialHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (requestCount) {
                            0 -> {
                                requestCount++
                                respond(
                                    """
                                {
                                  "errcode": "M_NOT_FOUND",
                                  "flows":[
                                    {
                                      "stages":["m.login.password"]
                                    },
                                    {
                                      "stages":["m.login.sso","m.login.recaptcha"]
                                    }
                                  ],
                                  "params":{
                                      "example.type.baz":{
                                          "example_key":"foobar"
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
                }
            },
            json = json,
            accessToken = MutableStateFlow("token")
        )

        val result = cut.uiaRequest<Map<String, String>, OkResponse>(
            body = mapOf("help" to "me")
        ) {
            method = Post
            url("/path")
            parameter("param", "dino")
        }.getOrThrow()
        result.shouldBeInstanceOf<UIA.UIAError<*>>()
        result.state shouldBe UIAState(
            completed = listOf(),
            flows = setOf(
                UIAState.FlowInformation(listOf(AuthenticationType.Password)),
                UIAState.FlowInformation(listOf(AuthenticationType.SSO, AuthenticationType.Recaptcha)),
            ),
            parameter = JsonObject(
                mapOf(
                    "example.type.baz" to JsonObject(
                        mapOf(
                            "example_key" to JsonPrimitive("foobar")
                        )
                    )
                )
            ),
            session = "session1"
        )
        result.errorResponse shouldBe ErrorResponse.NotFound()
        result.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
        result.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
        requestCount shouldBe 2
    }
}