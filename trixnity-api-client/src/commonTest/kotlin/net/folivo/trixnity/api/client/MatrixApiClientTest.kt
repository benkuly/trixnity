package net.folivo.trixnity.api.client

import io.kotest.assertions.shouldFail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.resources.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixApiClientTest {

    private val json = createMatrixJson()

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

    @Test
    fun itShouldDoNormalRequest() = runTest {
        val cut = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("matrix.host", request.url.host)
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
        )

        cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow() shouldBe PostPath.Response("ok")
    }

    @Test
    fun itShouldCatchNotOkResponseAndThrowMatrixServerException() = runTest {
        val cut = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
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
            },
            json = json,
        )
        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        assertEquals(HttpStatusCode.NotFound, error.statusCode)
        assertEquals(
            ErrorResponse.Forbidden::class,
            error.errorResponse::class
        )
        assertEquals("Only unicorns accepted", error.errorResponse.error)
    }

    @Test
    fun itShouldCatchAllOtherNotOkResponseAndThrowMatrixServerException() = runTest {
        val cut = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler {
                    respond(
                        "NO_UNICORN",
                        HttpStatusCode.NotFound,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
        )
        val error = shouldThrow<MatrixServerException> {
            cut.request(PostPath("1", "2"), PostPath.Request(true)).getOrThrow()
        }
        assertEquals(HttpStatusCode.NotFound, error.statusCode)
        assertEquals(
            ErrorResponse.CustomErrorResponse::class,
            error.errorResponse::class
        )
        assertEquals("NO_UNICORN", error.errorResponse.error)
    }

    /**
     *  When this test fails, it is likely because https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed.
     *  -> Remove all .e() calls in the ApiClients.
     */
    @Test
    fun ktor3953Test() = runTest {
        @Serializable
        @Resource("/{param}")
        data class RequestResource(
            @SerialName("param") val param: String,
        )

        val httpClient = HttpClient(MockEngine) {
            install(io.ktor.client.plugins.resources.Resources)
            engine {
                addHandler { request ->
                    assertEquals(
                        "/%21dino%3A%2B%2Eunicorn%2F",
                        request.url.fullPath
                    )
                    respond("{}", HttpStatusCode.OK)
                }
            }
        }
        httpClient.get(RequestResource("!dino:+.unicorn/".e())) // works
        shouldFail {
            httpClient.get(RequestResource("!dino:+.unicorn/")) // fails
        }
    }
}