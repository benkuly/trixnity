package net.folivo.trixnity.clientserverapi.server

import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders.AccessControlAllowHeaders
import io.ktor.http.HttpHeaders.AccessControlAllowMethods
import io.ktor.http.HttpHeaders.AccessControlAllowOrigin
import io.ktor.http.HttpHeaders.AccessControlRequestMethod
import io.ktor.http.HttpHeaders.Origin
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.Test

class MatrixClientServerApiServerTest {
    @Mock
    val authenticationApiHandlerMock = mock(classOf<AuthenticationApiHandler>())

    @Mock
    val devicesApiHandlerMock = mock(classOf<DevicesApiHandler>())

    @Mock
    val keysApiHandlerMock = mock(classOf<KeysApiHandler>())

    @Mock
    val mediaApiHandlerMock = mock(classOf<MediaApiHandler>())

    @Mock
    val pushApiHandlerMock = mock(classOf<PushApiHandler>())

    val roomsApiHandlerMock = RoomsApiHandlerMock()

    @Mock
    val serverApiHandlerMock = mock(classOf<ServerApiHandler>())

    @Mock
    val syncApiHandlerMock = mock(classOf<SyncApiHandler>())

    @Mock
    val usersApiHandlerMock = mock(classOf<UsersApiHandler>())

    private fun ApplicationTestBuilder.initCut() {
        application {
            this.matrixClientServerApiServer(
                accessTokenAuthenticationFunction = {
                    AccessTokenAuthenticationFunctionResult(
                        UserIdPrincipal("user"),
                        null
                    )
                },
                authenticationApiHandler = authenticationApiHandlerMock,
                devicesApiHandler = devicesApiHandlerMock,
                keysApiHandler = keysApiHandlerMock,
                mediaApiHandler = mediaApiHandlerMock,
                pushApiHandler = pushApiHandlerMock,
                roomsApiHandler = roomsApiHandlerMock,
                serverApiHandler = serverApiHandlerMock,
                syncApiHandler = syncApiHandlerMock,
                usersApiHandler = usersApiHandlerMock,
            )
        }
    }

    @Test
    fun shouldAllowDiscoverCORSHeader() = testApplication {
        initCut()
        val response = client.options("/_matrix/client/v3/joined_rooms") {
            header(Origin, "https://localhost:2424")
            header(AccessControlRequestMethod, "GET")
        }
        response.status shouldBe OK
        response.headers[AccessControlAllowOrigin] shouldBe "*"
        response.headers[AccessControlAllowMethods] shouldBe "DELETE, OPTIONS, PUT"
        response.headers[AccessControlAllowHeaders] shouldBe "Authorization, Content-Type, X-Requested-With"
    }
}