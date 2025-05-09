package net.folivo.trixnity.api.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixApiClientTest : TrixnityBaseTest() {
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
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/path/1?requestParam=2", request.url.fullPath)
                    assertEquals("localhost", request.url.host)
                    val contentType = request.body.contentType?.toString()
                        ?: request.body.headers[HttpHeaders.ContentType]
                        ?: request.headers[HttpHeaders.ContentType]
                    assertEquals(Application.Json.toString(), contentType)
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
            httpClientEngine = scopedMockEngine {
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
    object GetPath : MatrixEndpoint<Unit, GetPath.Response> {
        @Serializable
        data class Response(
            val status: String
        )
    }

    @Test
    fun itShouldForceJsonResponseDeserialization() = runTest {
        val cut = MatrixApiClient(
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/path", request.url.fullPath)
                    assertEquals("localhost", request.url.host)
                    val contentType = request.body.contentType?.toString()
                        ?: request.body.headers[HttpHeaders.ContentType]
                        ?: request.headers[HttpHeaders.ContentType]
                    assertEquals(null, contentType)
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
            httpClientEngine = scopedMockEngine {
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
        error.statusCode shouldBe HttpStatusCode.NotFound
        error.errorResponse shouldBe ErrorResponse.Forbidden("Only unicorns accepted")
    }

    @Test
    fun itShouldCatchNotOkErrorResponseAndThrowMatrixServerException() = runTest {
        val cut = MatrixApiClient(
            httpClientEngine = scopedMockEngine {
                addHandler {
                    respond(
                        """{
                            "errcodeWRONG": "M_FORBIDDEN",
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
        error.statusCode shouldBe HttpStatusCode.NotFound
        error.errorResponse.shouldBeInstanceOf<ErrorResponse.BadJson>().error shouldStartWith "response could not be parsed"
    }

    @Test
    fun itShouldCatchAllOtherNotOkResponseAndThrowMatrixServerException() = runTest {
        val cut = MatrixApiClient(
            httpClientEngine = scopedMockEngine {
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
            ErrorResponse.NotJson::class,
            error.errorResponse::class
        )
        error.errorResponse.error shouldStartWith "response could not be parsed"
    }
}