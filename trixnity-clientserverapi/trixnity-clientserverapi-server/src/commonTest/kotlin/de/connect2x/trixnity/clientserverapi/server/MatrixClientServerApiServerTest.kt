package de.connect2x.trixnity.clientserverapi.server

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
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MatrixClientServerApiServerTest : TrixnityBaseTest() {
    val appserviceApiHandlerMock = mock<AppserviceApiHandler>()
    val authenticationApiHandlerMock = mock<AuthenticationApiHandler>()
    val discoveryApiHandlerMock = mock<DiscoveryApiHandler>()
    val deviceApiHandlerMock = mock<DeviceApiHandler>()
    val keyApiHandlerMock = mock<KeyApiHandler>()
    val mediaApiHandlerMock = mock<MediaApiHandler>()
    val pushApiHandlerMock = mock<PushApiHandler>()
    val roomApiHandlerMock = mock<RoomApiHandler>()
    val serverApiHandlerMock = mock<ServerApiHandler>()
    val syncApiHandlerMock = mock<SyncApiHandler>()
    val userApiHandlerMock = mock<UserApiHandler>()

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
                    deviceApiHandler = deviceApiHandlerMock,
                    discoveryApiHandler = discoveryApiHandlerMock,
                    keyApiHandler = keyApiHandlerMock,
                    mediaApiHandler = mediaApiHandlerMock,
                    pushApiHandler = pushApiHandlerMock,
                    roomApiHandler = roomApiHandlerMock,
                    serverApiHandler = serverApiHandlerMock,
                    syncApiHandler = syncApiHandlerMock,
                    userApiHandler = userApiHandlerMock,
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
            deviceApiHandlerMock,
            keyApiHandlerMock,
            mediaApiHandlerMock,
            pushApiHandlerMock,
            roomApiHandlerMock,
            serverApiHandlerMock,
            syncApiHandlerMock,
            userApiHandlerMock,
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