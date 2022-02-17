package net.folivo.trixnity.appservice

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

private fun Application.testAppMatrixQueryParameterAuth() {
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
    fun `should forbid missing token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(
            Json.encodeToString(net.folivo.trixnity.clientserverapi.model.ErrorResponse.Unauthorized()),
            response.body()
        )
    }

    @Test
    fun `should forbid wrong token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something?access_token=invalidToken")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(
            Json.encodeToString(net.folivo.trixnity.clientserverapi.model.ErrorResponse.Forbidden()),
            response.body()
        )
    }

    @Test
    fun `should permit right token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}