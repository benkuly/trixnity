package net.folivo.trixnity.api.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MatrixApiClientTest {

    private val json = createMatrixEventJson()

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
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("localhost", request.url.host)
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

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    data class PostUnitPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<Unit, Unit>

    @Test
    fun itShouldAllowSendUnitPostRequest() = runTest {
        val cut = MatrixApiClient(
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("localhost", request.url.host)
                    assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals(Post, request.method)
                    assertEquals("""{}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
            json = json,
        )

        cut.request(PostUnitPath("1", "2")).getOrThrow()
    }

    @Serializable
    @Resource("/path")
    @HttpMethod(GET)
    @ForceJson
    object GetPath : MatrixEndpoint<Unit, GetPath.Response> {
        @Serializable
        data class Response(
            val status: String
        )
    }

    @Test
    fun itShouldForceJsonResponseDeserialization() = runTest {
        val cut = MatrixApiClient(
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/path", request.url.fullPath)
                    assertEquals("localhost", request.url.host)
                    assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals(Get, request.method)
                    respond("""{"status":"ok"}""", HttpStatusCode.OK)
                }
            },
            json = json,
        )

        cut.request(GetPath).getOrThrow() shouldBe GetPath.Response("ok")
    }

    @Test
    fun itShouldCatchNotOkResponseAndThrowMatrixServerException() = runTest {
        val cut = MatrixApiClient(
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
}