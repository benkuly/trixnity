package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.client.api.MatrixServerException
import org.junit.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

fun Application.testAppAppserviceModule() {
    matrixAppserviceModule(MatrixAppserviceProperties("token"), mockk())
    routing {
        get("/error") {
            throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse("OH", "no"))
        }
        authenticate("default") {
            get("/authenticated") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

class AppserviceModuleTest {

    @Test
    fun `should catch errors`() = withTestApplication(Application::testAppAppserviceModule) {
        val response = handleRequest(HttpMethod.Get, "/error").response
        assertEquals(HttpStatusCode.InternalServerError, response.status())
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(Json.encodeToString(ErrorResponse("OH", "no")), response.content)
    }

    @Test
    fun `should authenticate request`() = withTestApplication(Application::testAppAppserviceModule) {
        val response1 = handleRequest(HttpMethod.Get, "/authenticated?access_token=token").response
        assertEquals(HttpStatusCode.OK, response1.status())
        val response2 = handleRequest(HttpMethod.Get, "/authenticated").response
        assertEquals(HttpStatusCode.Unauthorized, response2.status())
    }
}