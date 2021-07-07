package net.folivo.trixnity.appservice.rest.room

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.rest.MatrixRestClient
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.room.Visibility
import net.folivo.trixnity.core.model.MatrixId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class AppserviceRoomServiceTest {

    class TestAppserviceRoomService(override val matrixRestClient: MatrixRestClient) : AppserviceRoomService {
        override suspend fun roomExistingState(roomAlias: MatrixId.RoomAliasId): AppserviceRoomService.RoomExistingState {
            throw RuntimeException("this is not tested")
        }

        override suspend fun getCreateRoomParameter(roomAlias: MatrixId.RoomAliasId): CreateRoomParameter {
            throw RuntimeException("this is not tested")
        }

        override suspend fun onCreatedRoom(roomAlias: MatrixId.RoomAliasId, roomId: MatrixId.RoomId) {
            throw RuntimeException("this is not tested")
        }

    }

    private val matrixRestClientMock: MatrixRestClient = mockk()
    private val cut = spyk(TestAppserviceRoomService(matrixRestClientMock))

    @BeforeTest
    fun beforeEach() {
        coEvery { cut.onCreatedRoom(any(), any()) } just Runs
        coEvery { cut.getCreateRoomParameter(any()) }
            .returns(CreateRoomParameter())
    }

    @Test
    fun `should create and save room`() {
        coEvery { cut.getCreateRoomParameter(MatrixId.RoomAliasId("alias", "server")) }
            .returns(CreateRoomParameter(name = "someName"))

        coEvery { matrixRestClientMock.room.createRoom(allAny()) }
            .returns(MatrixId.RoomId("room", "server"))

        runBlocking { cut.createManagedRoom(MatrixId.RoomAliasId("alias", "server")) }

        coVerify {
            matrixRestClientMock.room.createRoom(
                roomAliasId = MatrixId.RoomAliasId("alias", "server"),
                visibility = Visibility.PUBLIC,
                name = "someName"
            )
            cut.onCreatedRoom(MatrixId.RoomAliasId("alias", "server"), any())
        }
    }

    @Test
    fun `should have error when creation fails`() {
        coEvery { matrixRestClientMock.room.createRoom(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("500", "M_UNKNOWN")
                )
            )

        try {
            runBlocking { cut.createManagedRoom(MatrixId.RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }

        coVerify(exactly = 0) { cut.onCreatedRoom(any(), any()) }
    }

    @Test
    fun `should have error when saving by room service fails`() {
        coEvery { cut.onCreatedRoom(MatrixId.RoomAliasId("alias", "server"), any()) }
            .throws(RuntimeException())

        coEvery { matrixRestClientMock.room.createRoom(allAny()) }
            .returns(MatrixId.RoomId("room", "server"))

        try {
            runBlocking { cut.createManagedRoom(MatrixId.RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify { cut.onCreatedRoom(any(), any()) }
    }
}