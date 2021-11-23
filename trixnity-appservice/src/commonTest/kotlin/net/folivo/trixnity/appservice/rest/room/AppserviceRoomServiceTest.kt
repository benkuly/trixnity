package net.folivo.trixnity.appservice.rest.room

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.MatrixServerException
import net.folivo.trixnity.client.api.rooms.Visibility
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class AppserviceRoomServiceTest {

    class TestAppserviceRoomService(override val matrixApiClient: MatrixApiClient) : AppserviceRoomService {
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

    private val matrixApiClientMock: MatrixApiClient = mockk()
    private val cut = spyk(TestAppserviceRoomService(matrixApiClientMock))

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

        coEvery { matrixApiClientMock.rooms.createRoom(allAny()) }
            .returns(RoomId("room", "server"))

        runBlocking { cut.createManagedRoom(RoomAliasId("alias", "server")) }

        coVerify {
            matrixApiClientMock.rooms.createRoom(
                roomAliasId = RoomAliasId("alias", "server"),
                visibility = Visibility.PUBLIC,
                name = "someName"
            )
            cut.onCreatedRoom(RoomAliasId("alias", "server"), any())
        }
    }

    @Test
    fun `should have error when creation fails`() {
        coEvery { matrixApiClientMock.rooms.createRoom(allAny()) }
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

        coEvery { matrixApiClientMock.rooms.createRoom(allAny()) }
            .returns(RoomId("room", "server"))

        try {
            runBlocking { cut.createManagedRoom(RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify { cut.onCreatedRoom(any(), any()) }
    }
}