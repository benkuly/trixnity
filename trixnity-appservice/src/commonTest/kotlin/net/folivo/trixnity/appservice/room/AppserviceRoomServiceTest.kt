package net.folivo.trixnity.appservice.room

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.clientserverapi.model.ErrorResponse
import net.folivo.trixnity.clientserverapi.model.rooms.Visibility
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class AppserviceRoomServiceTest {

    class TestAppserviceRoomService(override val matrixClientServerApiClient: MatrixClientServerApiClient) :
        AppserviceRoomService {
        override suspend fun roomExistingState(roomAlias: RoomAliasId): AppserviceRoomService.RoomExistingState {
            throw RuntimeException("this is not tested")
        }

        override suspend fun getCreateRoomParameter(roomAlias: RoomAliasId): CreateRoomParameter {
            throw RuntimeException("this is not tested")
        }

        override suspend fun onCreatedRoom(roomAlias: RoomAliasId, roomId: RoomId) {
            throw RuntimeException("this is not tested")
        }

    }

    private val matrixClientServerApiClientMock: MatrixClientServerApiClient = mockk()
    private val cut = spyk(TestAppserviceRoomService(matrixClientServerApiClientMock))

    @BeforeTest
    fun beforeEach() {
        coEvery { cut.onCreatedRoom(any(), any()) } just Runs
        coEvery { cut.getCreateRoomParameter(any()) }
            .returns(CreateRoomParameter())
    }

    @Test
    fun `should create and save room`() {
        coEvery { cut.getCreateRoomParameter(RoomAliasId("alias", "server")) }
            .returns(CreateRoomParameter(name = "someName"))

        coEvery { matrixClientServerApiClientMock.rooms.createRoom(allAny()) }
            .returns(Result.success(RoomId("room", "server")))

        runBlocking { cut.createManagedRoom(RoomAliasId("alias", "server")) }

        coVerify {
            matrixClientServerApiClientMock.rooms.createRoom(
                roomAliasId = RoomAliasId("alias", "server"),
                visibility = Visibility.PUBLIC,
                name = "someName"
            )
            cut.onCreatedRoom(RoomAliasId("alias", "server"), any())
        }
    }

    @Test
    fun `should have error when creation fails`() {
        coEvery { matrixClientServerApiClientMock.rooms.createRoom(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse.Unknown("internal server error")
                )
            )

        try {
            runBlocking { cut.createManagedRoom(RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }

        coVerify(exactly = 0) { cut.onCreatedRoom(any(), any()) }
    }

    @Test
    fun `should have error when saving by room service fails`() {
        coEvery { cut.onCreatedRoom(RoomAliasId("alias", "server"), any()) }
            .throws(RuntimeException())

        coEvery { matrixClientServerApiClientMock.rooms.createRoom(allAny()) }
            .returns(Result.success(RoomId("room", "server")))

        try {
            runBlocking { cut.createManagedRoom(RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify { cut.onCreatedRoom(any(), any()) }
    }
}