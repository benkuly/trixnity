package net.folivo.trixnity.appservice.rest

import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.appservice.rest.event.AppserviceEventService
import net.folivo.trixnity.appservice.rest.room.AppserviceRoomService
import net.folivo.trixnity.appservice.rest.room.CreateRoomParameter
import net.folivo.trixnity.appservice.rest.user.AppserviceUserService
import net.folivo.trixnity.appservice.rest.user.RegisterUserParameter
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class DefaultAppserviceHandlerTest {

    private val appserviceEventServiceMock: AppserviceEventService = mockk()
    private val appserviceUserServiceMock: AppserviceUserService = mockk()
    private val appserviceRoomServiceMock: AppserviceRoomService = mockk()

    private val cut =
        DefaultAppserviceService(appserviceEventServiceMock, appserviceUserServiceMock, appserviceRoomServiceMock)

    @BeforeTest
    fun beforeEach() {
        coEvery { appserviceUserServiceMock.userExistingState(allAny()) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)
        coEvery { appserviceUserServiceMock.getRegisterUserParameter(allAny()) }
            .returns(RegisterUserParameter())
        coEvery { appserviceUserServiceMock.onRegisteredUser(allAny()) } just Runs
        coEvery { appserviceUserServiceMock.registerManagedUser(any()) } just Runs
        coEvery { appserviceRoomServiceMock.createManagedRoom(any()) } just Runs
    }

    @Test
    fun `should process one event and ignore other`() {
        coEvery { appserviceEventServiceMock.eventProcessingState("someTnxId", MatrixId.EventId("event1", "server")) }
            .returns(AppserviceEventService.EventProcessingState.NOT_PROCESSED)
        coEvery { appserviceEventServiceMock.eventProcessingState("someTnxId", MatrixId.EventId("event2", "server")) }
            .returns(AppserviceEventService.EventProcessingState.PROCESSED)
        coEvery { appserviceEventServiceMock.processEvent(any()) } just Runs
        coEvery { appserviceEventServiceMock.onEventProcessed(any(), any()) } just Runs

        val events = arrayOf<Event.RoomEvent<*>>(
            mockk {
                every { id } returns MatrixId.EventId("event1", "server")
            },
            mockk {
                every { id } returns MatrixId.EventId("event2", "server")
            }
        )

        runBlocking { cut.addTransactions("someTnxId", flowOf(*events)) }

        coVerify(exactly = 1) { appserviceEventServiceMock.processEvent(events[0]) }
        coVerify(exactly = 1) {
            appserviceEventServiceMock.onEventProcessed(
                "someTnxId",
                MatrixId.EventId("event1", "server")
            )
        }
    }

    @Test
    fun `should process event without id`() {
        coEvery { appserviceEventServiceMock.processEvent(any()) } just Runs
        coEvery { appserviceEventServiceMock.onEventProcessed(any(), any()) } just Runs

        val events = arrayOf(
            mockk<Event.BasicEvent<*>>()
        )

        runBlocking { cut.addTransactions("someTnxId", flowOf(*events)) }

        coVerify(exactly = 1) { appserviceEventServiceMock.processEvent(events[0]) }
        coVerify(exactly = 0) { appserviceEventServiceMock.eventProcessingState(any(), any()) }
        coVerify(exactly = 0) { appserviceEventServiceMock.onEventProcessed(any(), any()) }
    }

    @Test
    fun `should not process other events on error`() {
        val event1 = mockk<Event.RoomEvent<*>> {
            every { id } returns MatrixId.EventId("event1", "server")
        }
        val event2 = mockk<Event.RoomEvent<*>> {
            every { id } returns MatrixId.EventId("event2", "server")
        }

        coEvery { appserviceEventServiceMock.eventProcessingState(any(), any()) }
            .returns(AppserviceEventService.EventProcessingState.NOT_PROCESSED)
        coEvery { appserviceEventServiceMock.processEvent(any()) }
            .throws(RuntimeException())
        coEvery { appserviceEventServiceMock.onEventProcessed(any(), any()) } just Runs

        try {
            runBlocking { cut.addTransactions("someTnxId", flowOf(event1, event2)) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify(exactly = 1) { appserviceEventServiceMock.processEvent(event1) }
        coVerify(exactly = 0) { appserviceEventServiceMock.processEvent(event2) }
        coVerify(exactly = 0) { appserviceEventServiceMock.onEventProcessed("someTnxId", any()) }
    }


    @Test
    fun `should hasUser when delegated service says it exists`() {
        coEvery { appserviceUserServiceMock.userExistingState(MatrixId.UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.EXISTS)

        val hasUser = runBlocking { cut.hasUser(MatrixId.UserId("user", "server")) }
        hasUser shouldBe true
    }

    @Test
    fun `should hasUser and create it when delegated service want to`() {
        coEvery { appserviceUserServiceMock.userExistingState(MatrixId.UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)

        val hasUser = runBlocking { cut.hasUser(MatrixId.UserId("user", "server")) }
        hasUser shouldBe true

        coVerify { appserviceUserServiceMock.registerManagedUser(MatrixId.UserId("user", "server")) }
    }

    @Test
    fun `should have error when helper fails`() {
        coEvery { appserviceUserServiceMock.userExistingState(MatrixId.UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)
        coEvery { appserviceUserServiceMock.registerManagedUser(any()) }
            .throws(RuntimeException())

        try {
            runBlocking { cut.hasUser(MatrixId.UserId("user", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }
    }

    @Test
    fun `should not hasUser when delegated service says it does not exists and should not be created`() {
        coEvery { appserviceUserServiceMock.userExistingState(MatrixId.UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.DOES_NOT_EXISTS)

        val hasUser = runBlocking { cut.hasUser(MatrixId.UserId("user", "server")) }
        hasUser shouldBe false

        coVerify(exactly = 0) { appserviceUserServiceMock.registerManagedUser(any()) }
    }

    @Test
    fun `should hasRoomAlias when delegated service says it exists`() {
        coEvery { appserviceRoomServiceMock.roomExistingState(MatrixId.RoomAliasId("alias", "server")) }
            .returns(AppserviceRoomService.RoomExistingState.EXISTS)

        val hasRoom = runBlocking { cut.hasRoomAlias(MatrixId.RoomAliasId("alias", "server")) }
        hasRoom shouldBe true
    }

    @Test
    fun `should hasRoomAlias and create it when delegated service want to`() {
        coEvery { appserviceRoomServiceMock.roomExistingState(MatrixId.RoomAliasId("alias", "server")) }
            .returns(AppserviceRoomService.RoomExistingState.CAN_BE_CREATED)
        coEvery { appserviceRoomServiceMock.getCreateRoomParameter(MatrixId.RoomAliasId("alias", "server")) }
            .returns(CreateRoomParameter(name = "someName"))

        val hasRoom = runBlocking { cut.hasRoomAlias(MatrixId.RoomAliasId("alias", "server")) }
        hasRoom shouldBe true

        coVerify { appserviceRoomServiceMock.createManagedRoom(MatrixId.RoomAliasId("alias", "server")) }
    }

    @Test
    fun `should not hasRoomAlias when creation fails`() {
        coEvery { appserviceRoomServiceMock.roomExistingState(MatrixId.RoomAliasId("alias", "server")) }
            .returns(AppserviceRoomService.RoomExistingState.CAN_BE_CREATED)

        coEvery { appserviceRoomServiceMock.createManagedRoom(any()) }.throws(RuntimeException())

        try {
            runBlocking { cut.hasRoomAlias(MatrixId.RoomAliasId("alias", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }
    }

    @Test
    fun `should not hasRoomAlias when delegated service says it does not exists and should not be created`() {
        coEvery { appserviceRoomServiceMock.roomExistingState(MatrixId.RoomAliasId("alias", "server")) }
            .returns(AppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS)

        val hasRoom = runBlocking { cut.hasRoomAlias(MatrixId.RoomAliasId("alias", "server")) }
        hasRoom shouldBe false

        coVerify(exactly = 0) { appserviceRoomServiceMock.createManagedRoom(any()) }
    }
}