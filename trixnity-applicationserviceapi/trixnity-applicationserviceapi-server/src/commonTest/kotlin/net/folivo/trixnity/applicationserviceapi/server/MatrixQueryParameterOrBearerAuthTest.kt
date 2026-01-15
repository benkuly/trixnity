package net.folivo.trixnity.applicationserviceapi.server

import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun Application.testAppMatrixQueryParameterAuth() {
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        matrixQueryParameterOrBearer(null, "access_token", "validToken")
    }
    routing {
        authenticate {
            get("/_matrix/something") {
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

class MatrixQueryParameterOrBearerAuthTest : TrixnityBaseTest() {

    @Test
    fun `should forbid missing query or auth token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
        Json.decodeFromString(ErrorResponse.Serializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.Unauthorized>()
    }

    @Test
    fun `should forbid wrong query token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something?access_token=invalidToken")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
        Json.decodeFromString(ErrorResponse.Serializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.Forbidden>()
    }

    @Test
    fun `should forbid wrong header token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something") {
            bearerAuth("invalidToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
        Json.decodeFromString(ErrorResponse.Serializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.Forbidden>()
    }

    @Test
    fun `should forbid non matching token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something?access_token=validToken") {
            bearerAuth("invalidToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), response.contentType())
        Json.decodeFromString(ErrorResponse.Serializer, response.body())
            .shouldBeInstanceOf<ErrorResponse.Forbidden>()
    }

    @Test
    fun `should permit right query token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something?access_token=validToken")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should permit right header token`() = testApplication {
        application { testAppMatrixQueryParameterAuth() }
        val response = client.get("/_matrix/something") {
            bearerAuth("validToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}