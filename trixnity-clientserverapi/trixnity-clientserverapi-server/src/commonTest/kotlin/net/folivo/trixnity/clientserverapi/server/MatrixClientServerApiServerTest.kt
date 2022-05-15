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
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class MatrixClientServerApiServerTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    @Mock
    lateinit var authenticationApiHandlerMock: AuthenticationApiHandler

    @Mock
    lateinit var discoveryApiHandlerMock: DiscoveryApiHandler

    @Mock
    lateinit var devicesApiHandlerMock: DevicesApiHandler

    @Mock
    lateinit var keysApiHandlerMock: KeysApiHandler

    @Mock
    lateinit var mediaApiHandlerMock: MediaApiHandler

    @Mock
    lateinit var pushApiHandlerMock: PushApiHandler

    @Mock
    lateinit var roomsApiHandlerMock: RoomsApiHandler

    @Mock
    lateinit var serverApiHandlerMock: ServerApiHandler

    @Mock
    lateinit var syncApiHandlerMock: SyncApiHandler

    @Mock
    lateinit var usersApiHandlerMock: UsersApiHandler

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
                discoveryApiHandler = discoveryApiHandlerMock,
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