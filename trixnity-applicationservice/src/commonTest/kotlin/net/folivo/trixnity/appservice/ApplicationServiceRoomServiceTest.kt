package net.folivo.trixnity.appservice

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.mockEngineFactoryWithEndpoints
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationServiceRoomServiceTest {

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    private val roomAlias = RoomAliasId("alias", "server")
    private val roomId = RoomId("room", "server")

    @Test
    fun `should create and save room`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientFactory = mockEngineFactoryWithEndpoints(json, mappings) {
                matrixJsonEndpoint(CreateRoom()) { requestBody ->
                    assertSoftly(requestBody) {
                        it.roomAliasLocalPart shouldBe roomAlias.localpart
                        it.name shouldBe "someName"
                    }
                    CreateRoom.Response(roomId)
                }
            })
        val cut = TestApplicationServiceRoomService(api)

        cut.createRoomParameter = CreateRoomParameter(name = "someName")

        cut.createManagedRoom(roomAlias)

        cut.createRoomParameterCalled shouldBe roomAlias
        cut.onCreateRoomCalled shouldBe (roomAlias to roomId)
    }

    @Test
    fun `should have error when creation fails`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientFactory = mockEngineFactoryWithEndpoints(json, mappings) {
                matrixJsonEndpoint(CreateRoom()) {
                    throw MatrixServerException(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse.Unknown("internal server error")
                    )
                }
            })
        val cut = TestApplicationServiceRoomService(api)
        cut.createRoomParameter = CreateRoomParameter()

        shouldThrow<MatrixServerException> {
            cut.createManagedRoom(roomAlias)
        }

        cut.onCreateRoomCalled shouldBe null
    }

    @Test
    fun `should have error when saving by room service fails`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientFactory = mockEngineFactoryWithEndpoints(json, mappings) {
                matrixJsonEndpoint(CreateRoom()) {
                    CreateRoom.Response(roomId)
                }
            })
        val cut = TestApplicationServiceRoomService(api)

        cut.createRoomParameter = CreateRoomParameter()
        cut.onCreateRoom = Result.failure(RuntimeException())

        shouldThrow<RuntimeException> {
            cut.createManagedRoom(roomAlias)
        }
    }
}