package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.model.ErrorResponse
import org.junit.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

fun Application.testAppMatrixQueryParameterAuth() {
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        matrixQueryParameter("default", "access_token", "validToken")
    }
    install(Routing) {
        authenticate("default") {
            get("/_matrix/something") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

class MatrixQueryParameterAuthTest {

    @Test
    fun `should forbid missing token`() = withTestApplication(Application::testAppMatrixQueryParameterAuth) {
        val response = handleRequest(HttpMethod.Get, "/_matrix/something").response
        assertEquals(HttpStatusCode.Unauthorized, response.status())
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(Json.encodeToString(ErrorResponse.Unauthorized()), response.content)
    }

    @Test
    fun `should forbid wrong token`() = withTestApplication(Application::testAppMatrixQueryParameterAuth) {
        val response = handleRequest(HttpMethod.Get, "/_matrix/something?access_token=invalidToken").response
        assertEquals(HttpStatusCode.Forbidden, response.status())
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(Json.encodeToString(ErrorResponse.Forbidden()), response.content)
    }

    @Test
    fun `should permit right token`() = withTestApplication(Application::testAppMatrixQueryParameterAuth) {
        val response = handleRequest(HttpMethod.Get, "/_matrix/something?access_token=validToken").response
        assertEquals(HttpStatusCode.OK, response.status())
    }
}