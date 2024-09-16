package net.folivo.trixnity.clientserverapi.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixClientServerApiHttpClientTest {

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()

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
    data class PostPathWithUIA(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixUIAEndpoint<PostPathWithUIA.Request, PostPathWithUIA.Response> {
        @Serializable
        data class Request(
            val includeDino: Boolean
        )

        @Serializable
        data class Response(
            val status: String
        )
    }


    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runTest {
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("matrix.host", request.url.host)
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals(Post, request.method)
                    assertEquals("""{"includeDino":true}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
            accessToken = MutableStateFlow("token")
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe PostPath.Response("ok")
    }

    @Test
    fun itShouldCallOnLogout() = runTest {
        var onLogout: Boolean? = null
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            accessToken = MutableStateFlow("token")
        )
        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        assertEquals(HttpStatusCode.Unauthorized, error.statusCode)
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        assertEquals("Only unicorns accepted", error.errorResponse.error)
        onLogout shouldBe true
    }

    @Test
    fun uiaRequestShouldReturnSuccess() = runTest {
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("matrix.host", request.url.host)
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals(Post, request.method)
                    assertEquals("""{"includeDino":true}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{"status":"ok"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
            accessToken = MutableStateFlow("token")
        )

        cut.uiaRequest(PostPathWithUIA("1", "2"), PostPathWithUIA.Request(true))
            .getOrThrow() shouldBe UIA.Success(PostPathWithUIA.Response("ok"))
    }

    @Test
    fun uiaRequestShouldReturnError() = runTest {
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler {
                    respond(
                        """{"errcode": "M_NOT_FOUND"}""",
                        HttpStatusCode.NotFound,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
            accessToken = MutableStateFlow("token")
        )

        val error = shouldThrow<MatrixServerException> {
            cut.uiaRequest(PostPathWithUIA("1", "2"), PostPathWithUIA.Request(true)).getOrThrow()
        }
        assertEquals(HttpStatusCode.NotFound, error.statusCode)
        assertEquals(
            ErrorResponse.NotFound::class,
            error.errorResponse::class
        )
    }

    @Test
    fun uiaRequestShouldCallOnLogout() = runTest {
        var onLogout: Boolean? = null
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            accessToken = MutableStateFlow("token")
        )

        val error = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPathWithUIA.Request(true)).getOrThrow()
            .shouldBeInstanceOf<UIA.Error<*>>()
        assertEquals(
            ErrorResponse.UnknownToken::class,
            error.errorResponse::class
        )
        onLogout shouldBe true
    }

    @Test
    fun uiaRequestShouldReturnStepAndAllowAuthenticate() = runTest {
        var requestCount = 0
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            accessToken = MutableStateFlow("token")
        )

        val result = cut.uiaRequest(PostPathWithUIA("1", "2"), PostPathWithUIA.Request(true)).getOrThrow()
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
        val cut = MatrixClientServerApiHttpClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = {
                HttpClient(MockEngine) {
                    it()
                    engine {
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
                    }
                }
            },
            json = json,
            eventContentSerializerMappings = mappings,
            accessToken = MutableStateFlow("token")
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
            PostPathWithUIA.Request(true)
        ).getOrThrow()
        result1.shouldBeInstanceOf<UIA.Error<*>>()
        result1.state shouldBe expectedUIAState
        result1.errorResponse shouldBe ErrorResponse.NotFound()
        result1.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
        val result2 = result1.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
            .getOrThrow()
        result2.shouldBeInstanceOf<UIA.Error<*>>()
        result2.state shouldBe expectedUIAState
        result2.errorResponse shouldBe ErrorResponse.NotFound()
        result2.getFallbackUrl(AuthenticationType.Password).toString() shouldBe
                "https://matrix.host/_matrix/client/v3/auth/m.login.password/fallback/web?session=session1"
        result2.authenticate(AuthenticationRequest.Password(IdentifierType.User("username"), "password"))
            .getOrThrow()
        requestCount shouldBe 3
    }
}