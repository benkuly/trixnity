package net.folivo.trixnity.appservice

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.appservice.rest.matrixAppserviceModule
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.clientserverapi.model.ErrorResponse
import org.junit.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

private fun Application.testAppAppserviceModule() {
    matrixAppserviceModule(MatrixAppserviceProperties("token"), mockk())
    routing {
        get("/error") {
            throw MatrixServerException(
                HttpStatusCode.InternalServerError,
                ErrorResponse.CustomErrorResponse("OH", "no")
            )
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
    fun `should catch errors`() = testApplication {
        application { testAppAppserviceModule() }
        val response = client.get("/error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charset.defaultCharset()), response.contentType())
        assertEquals(
            Json.encodeToString(
                net.folivo.trixnity.clientserverapi.model.ErrorResponse.CustomErrorResponse(
                    "OH",
                    "no"
                )
            ), response.body()
        )
    }

    @Test
    fun `should authenticate request`() = testApplication {
        application { testAppAppserviceModule() }
        val response1 = client.get("/authenticated?access_token=token")
        assertEquals(HttpStatusCode.OK, response1.status)
        val response2 = client.get("/authenticated")
        assertEquals(HttpStatusCode.Unauthorized, response2.status)
    }
}