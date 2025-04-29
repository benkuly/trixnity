package net.folivo.trixnity.appservice

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetDisplayName
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.subscribeEachEvent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.scopedMockEngine
import net.folivo.trixnity.testutils.scopedMockEngineWithEndpoints
import kotlin.test.Test

class DefaultApplicationServiceApiServerHandlerTest : TrixnityBaseTest() {

    private lateinit var applicationServiceEventTxnService: TestApplicationServiceEventTxnService
    private lateinit var applicationServiceUserService: TestApplicationServiceUserService
    private lateinit var applicationServiceRoomService: TestApplicationServiceRoomService

    private lateinit var cut: DefaultApplicationServiceApiServerHandler

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    private val userId = UserId("user", "server")
    private val roomAlias = RoomAliasId("alias", "server")

    private fun initCut(api: MatrixClientServerApiClientImpl) {
        applicationServiceEventTxnService = TestApplicationServiceEventTxnService()
        applicationServiceUserService = TestApplicationServiceUserService(api)
        applicationServiceRoomService = TestApplicationServiceRoomService(api)

        applicationServiceUserService.userExistingState =
            Result.success(ApplicationServiceUserService.UserExistingState.CAN_BE_CREATED)
        applicationServiceUserService.getRegisterUserParameter = Result.success(RegisterUserParameter())

        cut = DefaultApplicationServiceApiServerHandler(
            applicationServiceEventTxnService,
            applicationServiceUserService,
            applicationServiceRoomService
        )
    }

    @Test
    fun `should process events`() = runTest {
        val api = MatrixClientServerApiClientImpl(json = json, httpClientEngine = scopedMockEngine())
        initCut(api)

        val event = MessageEvent(
            RoomMessageEventContent.TextBased.Notice("hi"),
            EventId("event4"),
            userId,
            RoomId("room2", "server"),
            1234L
        )

        var allEventsCount = 0
        cut.subscribeEachEvent { allEventsCount++ }

        applicationServiceEventTxnService.eventTnxProcessingState = Result
            .success(ApplicationServiceEventTxnService.EventTnxProcessingState.PROCESSED)
        cut.addTransaction("sometxnId1", listOf(event))
        applicationServiceEventTxnService.onEventTnxProcessedCalled shouldBe null

        applicationServiceEventTxnService.eventTnxProcessingState = Result
            .success(ApplicationServiceEventTxnService.EventTnxProcessingState.NOT_PROCESSED)
        cut.addTransaction("sometxnId2", listOf(event))
        applicationServiceEventTxnService.onEventTnxProcessedCalled shouldBe "sometxnId2"

        allEventsCount shouldBe 1
    }

    @Test
    fun `should hasUser when delegated service says it exists`() = runTest {
        val api = MatrixClientServerApiClientImpl(json = json, httpClientEngine = scopedMockEngine())
        initCut(api)
        applicationServiceUserService.userExistingState =
            Result.success(ApplicationServiceUserService.UserExistingState.EXISTS)

        shouldNotThrow<MatrixServerException> {
            cut.hasUser(userId)
        }
    }

    @Test
    fun `should hasUser and create it when delegated service want to`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    ResponseWithUIA.Success(Register.Response(userId))
                }
                matrixJsonEndpoint(SetDisplayName(userId, userId)) { }
            })
        initCut(api)
        applicationServiceUserService.userExistingState =
            Result.success(ApplicationServiceUserService.UserExistingState.CAN_BE_CREATED)

        shouldNotThrow<MatrixServerException> {
            cut.hasUser(userId)
        }
    }

    @Test
    fun `should have error when helper fails`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown(""))
                }
            })
        initCut(api)
        applicationServiceUserService.userExistingState =
            Result.success(ApplicationServiceUserService.UserExistingState.CAN_BE_CREATED)

        shouldThrow<MatrixServerException> {
            cut.hasUser(userId)
        }
    }

    @Test
    fun `should not hasUser when delegated service says it does not exists and should not be created`() =
        runTest {
            val api = MatrixClientServerApiClientImpl(json = json, httpClientEngine = scopedMockEngine())
            initCut(api)
            applicationServiceUserService.userExistingState =
                Result.success(ApplicationServiceUserService.UserExistingState.DOES_NOT_EXISTS)

            shouldThrow<MatrixServerException> {
                cut.hasUser(userId)
            }.errorResponse.shouldBeInstanceOf<ErrorResponse.NotFound>()
        }

    @Test
    fun `should hasRoomAlias when delegated service says it exists`() = runTest {
        val api = MatrixClientServerApiClientImpl(json = json, httpClientEngine = scopedMockEngine())
        initCut(api)
        applicationServiceRoomService.roomExistingState = ApplicationServiceRoomService.RoomExistingState.EXISTS

        shouldNotThrow<MatrixServerException> {
            cut.hasRoomAlias(roomAlias)
        }
    }

    @Test
    fun `should hasRoomAlias and create it when delegated service want to`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(CreateRoom()) {
                    CreateRoom.Response(RoomId("room", "server"))
                }
            })
        initCut(api)
        applicationServiceRoomService.roomExistingState = ApplicationServiceRoomService.RoomExistingState.CAN_BE_CREATED
        applicationServiceRoomService.createRoomParameter = CreateRoomParameter(name = "someName")

        shouldNotThrow<MatrixServerException> {
            cut.hasRoomAlias(roomAlias)
        }
    }

    @Test
    fun `should not hasRoomAlias when creation fails`() = runTest {
        val api = MatrixClientServerApiClientImpl(
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(CreateRoom()) {
                    throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown(""))
                }
            })
        initCut(api)
        applicationServiceRoomService.roomExistingState = ApplicationServiceRoomService.RoomExistingState.CAN_BE_CREATED
        applicationServiceRoomService.createRoomParameter = CreateRoomParameter(name = "someName")

        shouldThrow<MatrixServerException> {
            cut.hasRoomAlias(roomAlias)
        }
    }

    @Test
    fun `should not hasRoomAlias when delegated service says it does not exists and should not be created`() =
        runTest {
            val api = MatrixClientServerApiClientImpl(json = json, httpClientEngine = scopedMockEngine())
            initCut(api)
            applicationServiceRoomService.roomExistingState =
                ApplicationServiceRoomService.RoomExistingState.DOES_NOT_EXISTS

            shouldThrow<MatrixServerException> {
                cut.hasRoomAlias(roomAlias)
            }.errorResponse.shouldBeInstanceOf<ErrorResponse.NotFound>()
        }
}