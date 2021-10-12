package net.folivo.trixnity.client.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MatrixApiClientTest {
    @Serializable
    data class OkResponse(
        val status: String = "default"
    )

    @Test
    fun itShouldHaveAuthenticationTokenIncludedAndDoNormalRequest() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            hostname = "matrix.host",
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/path?param=dino", request.url.fullPath)
                        assertEquals("matrix.host", request.url.host)
                        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                        assertEquals(Application.Json.toString(), request.headers[HttpHeaders.Accept])
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("""{"help":"me"}""", request.body.toByteArray().decodeToString())
                        respond(
                            """{"status":"ok"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            }).apply { accessToken = "token" }

        matrixRestClient.httpClient.post<OkResponse> {
            url("/path")
            parameter("param", "dino")
            body = mapOf("help" to "me")
        }
    }

    @Test
    fun itShouldCatchNotOkResponseAndThrowMatrixServerException() = runBlockingTest {
        try {
            val matrixRestClient = MatrixApiClient(
                hostname = "matrix.host",
                baseHttpClient = HttpClient(MockEngine) {
                    engine {
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
                })
            matrixRestClient.httpClient.post<OkResponse> {
                url("/path")
            }
            fail("should throw ${MatrixServerException::class.simpleName}")
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals("NO_UNICORN", error.errorResponse.errorCode)
            assertEquals("Only unicorns accepted", error.errorResponse.errorMessage)
        }
    }

    @Test
    fun itShouldCatchAllOtherNotOkResponseAndThrowMatrixServerException() = runBlockingTest {
        try {
            val matrixRestClient = MatrixApiClient(
                hostname = "matrix.host",
                baseHttpClient = HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            respond(
                                "NO_UNICORN",
                                HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, Application.Json.toString())
                            )
                        }
                    }
                })
            matrixRestClient.httpClient.post<OkResponse> {
                url("/path")
            }
            fail("should throw ${MatrixServerException::class.simpleName}")
        } catch (error: MatrixServerException) {
            assertEquals(HttpStatusCode.NotFound, error.statusCode)
            assertEquals("UNKNOWN", error.errorResponse.errorCode)
            assertEquals("NO_UNICORN", error.errorResponse.errorMessage)
        }
    }

}