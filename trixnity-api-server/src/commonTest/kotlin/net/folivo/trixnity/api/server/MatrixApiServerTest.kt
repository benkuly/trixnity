package net.folivo.trixnity.api.server

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
import kotlinx.serialization.SerializationException
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test

class MatrixApiServerTest {
    private val json = createMatrixJson()

    @Test
    fun shouldRespondMatrixServerExceptionOnMatrixServerException() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    get("/") {
                        throw MatrixServerException(HttpStatusCode.NotFound, ErrorResponse.NotFound("not found"))
                    }
                }
            }
        }
        val response = client.get("/")
        response.body<String>() shouldBe """{"errcode":"M_NOT_FOUND","error":"not found"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun shouldRespondMatrixServerExceptionOnSerializationException() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    get("/") {
                        throw SerializationException("missing key")
                    }
                }
            }
        }
        val response = client.get("/")
        response.body<String>() shouldBe """{"errcode":"M_BAD_JSON","error":"missing key"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun shouldRespondMatrixServerExceptionOnAllOtherExceptions() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    get("/") {
                        throw RuntimeException("something")
                    }
                }
            }
        }
        val response = client.get("/")
        response.body<String>() shouldBe """{"errcode":"M_UNKNOWN","error":"something"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.InternalServerError
    }
}