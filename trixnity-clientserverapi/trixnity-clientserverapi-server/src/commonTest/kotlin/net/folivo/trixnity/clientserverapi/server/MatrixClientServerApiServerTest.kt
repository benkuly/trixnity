package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.mock
import dev.mokkery.resetAnswers
import dev.mokkery.resetCalls
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders.AccessControlAllowHeaders
import io.ktor.http.HttpHeaders.AccessControlAllowMethods
import io.ktor.http.HttpHeaders.AccessControlAllowOrigin
import io.ktor.http.HttpHeaders.AccessControlRequestMethod
import io.ktor.http.HttpHeaders.Origin
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.*
import net.folivo.trixnity.core.model.UserId
import kotlin.test.BeforeTest
import kotlin.test.Test

class MatrixClientServerApiServerTest {
    val appserviceApiHandlerMock = mock<AppserviceApiHandler>()
    val authenticationApiHandlerMock = mock<AuthenticationApiHandler>()
    val discoveryApiHandlerMock = mock<DiscoveryApiHandler>()
    val devicesApiHandlerMock = mock<DevicesApiHandler>()
    val keysApiHandlerMock = mock<KeysApiHandler>()
    val mediaApiHandlerMock = mock<MediaApiHandler>()
    val pushApiHandlerMock = mock<PushApiHandler>()
    val roomsApiHandlerMock = mock<RoomsApiHandler>()
    val serverApiHandlerMock = mock<ServerApiHandler>()
    val syncApiHandlerMock = mock<SyncApiHandler>()
    val usersApiHandlerMock = mock<UsersApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            matrixClientServerApiServer(
                accessTokenAuthenticationFunction = {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ),
                        null
                    )
                },
            ) {
                matrixClientServerApiServerRoutes(
                    appserviceApiHandler = appserviceApiHandlerMock,
                    authenticationApiHandler = authenticationApiHandlerMock,
                    devicesApiHandler = devicesApiHandlerMock,
                    discoveryApiHandler = discoveryApiHandlerMock,
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
    }

    @BeforeTest
    fun beforeTest() {
        val mocks = listOf(
            appserviceApiHandlerMock,
            authenticationApiHandlerMock,
            discoveryApiHandlerMock,
            devicesApiHandlerMock,
            keysApiHandlerMock,
            mediaApiHandlerMock,
            pushApiHandlerMock,
            roomsApiHandlerMock,
            serverApiHandlerMock,
            syncApiHandlerMock,
            usersApiHandlerMock,
        ).toTypedArray()
        resetAnswers(*mocks)
        resetCalls(*mocks)
    }

    @Test
    fun shouldAllowDiscoverCORSHeader() = testApplication {
        initCut()
        val response = client.options("/_matrix/client/v3/joined_rooms") {
            header(Origin, "https://localhost:2424")
            header(AccessControlRequestMethod, "GET")
        }
        assertSoftly(response) {
            status shouldBe OK
            headers[AccessControlAllowOrigin] shouldBe "*"
            headers[AccessControlAllowMethods] shouldBe "DELETE, OPTIONS, PUT"
            headers[AccessControlAllowHeaders] shouldBe "Authorization, Content-Type, X-Requested-With"
        }
    }
}