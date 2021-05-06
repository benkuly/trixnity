package net.folivo.trixnity.appservice.rest

import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.appservice.rest.event.AppserviceEventTnxService
import net.folivo.trixnity.appservice.rest.room.AppserviceRoomService
import net.folivo.trixnity.appservice.rest.room.CreateRoomParameter
import net.folivo.trixnity.appservice.rest.user.AppserviceUserService
import net.folivo.trixnity.appservice.rest.user.RegisterUserParameter
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class DefaultAppserviceHandlerTest {

    private val appserviceEventTnxServiceMock: AppserviceEventTnxService = mockk()
    private val appserviceUserServiceMock: AppserviceUserService = mockk()
    private val appserviceRoomServiceMock: AppserviceRoomService = mockk()

    private val cut =
        DefaultAppserviceService(appserviceEventTnxServiceMock, appserviceUserServiceMock, appserviceRoomServiceMock)

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
    fun `should process events`() = runBlocking {
        coEvery { appserviceEventTnxServiceMock.eventTnxProcessingState("someTnxId1") }
            .returns(AppserviceEventTnxService.EventTnxProcessingState.PROCESSED)
        coEvery { appserviceEventTnxServiceMock.eventTnxProcessingState("someTnxId2") }
            .returns(AppserviceEventTnxService.EventTnxProcessingState.NOT_PROCESSED)
        coEvery { appserviceEventTnxServiceMock.onEventTnxProcessed(any()) } just Runs

        val event = Event.RoomEvent(
            MessageEventContent.NoticeMessageEventContent("hi"),
            MatrixId.EventId("event4", "server"),
            MatrixId.UserId("user", "server"),
            MatrixId.RoomId("room2", "server"),
            1234L
        )

        val emittedEventsFlow = cut.allEvents()
        val emittedEvents = async { emittedEventsFlow.take(1).toList() }
        launch {
            cut.addTransactions("someTnxId1", flowOf(event))
            cut.addTransactions("someTnxId2", flowOf(event))
        }

        emittedEvents.await().count().shouldBe(1)
        coVerify { appserviceEventTnxServiceMock.onEventTnxProcessed("someTnxId2") }
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