package net.folivo.trixnity.appservice.rest.api.user

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.MatrixClientProperties
import net.folivo.trixnity.client.rest.MatrixClientProperties.MatrixHomeServerProperties
import net.folivo.trixnity.client.rest.runBlockingTest
import net.folivo.trixnity.core.model.MatrixId.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
class UserApiClientTest {
    @Test
    fun shouldRegister() = runBlockingTest {
        val response = RegisterResponse(UserId("user", "server"))
        val expectedRequest = """
            {
              "auth":{
                "type":"someAuthenticationType",
                "session":"someAuthenticationSession"
              },
              "username":"someUsername",
              "password":"somePassword",
              "device_id":"someDeviceId",
              "initial_device_display_name":"someInitialDeviceDisplayName",
              "inhibit_login":true
            }
        """.trimIndent().lines().joinToString("") { it.trim() }
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/register?kind=user", request.url.fullPath)
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                respond(
                    Json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        val result = matrixClient.user.register(
            authenticationType = "someAuthenticationType",
            authenticationSession = "someAuthenticationSession",
            username = "someUsername",
            password = "somePassword",
            accountType = AccountType.USER,
            deviceId = "someDeviceId",
            initialDeviceDisplayName = "someInitialDeviceDisplayName",
            inhibitLogin = true
        )
        assertEquals(response, result)
    }

    @Test
    fun shouldSetDisplayName() = runBlockingTest {
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/profile/%40user%3Aserver/displayname", request.url.fullPath)
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("""{"displayname":"someDisplayName"}""", request.body.toByteArray().decodeToString())
                respond(
                    "{}",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        matrixClient.user.setDisplayName(UserId("user", "server"), "someDisplayName")
    }

    @Test
    fun shouldGetWhoami() = runBlockingTest {
        val response = WhoAmIResponse(UserId("user", "server"))
        val matrixClient = MatrixClient(
            properties = MatrixClientProperties(MatrixHomeServerProperties("matrix.host"), "token"),
            httpClientEngineFactory = MockEngine,
        ) {
            addHandler { request ->
                assertEquals("/_matrix/client/r0/account/whoami", request.url.fullPath)
                assertEquals(HttpMethod.Get, request.method)
                respond(
                    Json.encodeToString(response),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        }
        val result = matrixClient.user.whoAmI()
        assertEquals(UserId("user", "server"), result)
    }
}