package net.folivo.trixnity.client.rest

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.api.MatrixServerException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MatrixClientTest {
    @Serializable
    data class OkResponse(
        val status: String = "default"
    )

    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runBlockingTest {
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals(request.url.fullPath, "/_matrix/client/path?param=dino")
                assertEquals("matrix.host", request.url.host)
                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                assertEquals("POST", request.method.value)
                assertEquals(request.body.toByteArray().decodeToString(), """{"help":"me"}""")
                respond(
                    """{"status":"ok"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        matrixClient.httpClient.post<OkResponse> {
            url("/path")
            parameter("param", "dino")
            body = mapOf("help" to "me")
        }
    }

    @Test
    fun itShouldCatchNotOkResponseAndThrowMatrixServerException() = runBlockingTest {
        try {
            val matrixClient = MatrixClient(
                properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
                httpClientEngineFactory = MockEngine,
            ) {
                addHandler {
                    respond(
                        """{
                            "errcode": "NO_UNICORN",
                            "error": "Only unicorns accepted"
                       }""".trimIndent(),
                        HttpStatusCode.NotFound,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            }
            matrixClient.httpClient.post<OkResponse> {
                url("/path")
            }
            fail("should throw ${MatrixServerException::class.simpleName}")
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals("NO_UNICORN", error.errorResponse.errorCode)
            assertEquals("Only unicorns accepted", error.errorResponse.errorMessage)
        }
    }

}