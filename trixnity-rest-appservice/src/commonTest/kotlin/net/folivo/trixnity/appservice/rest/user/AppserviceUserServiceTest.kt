package net.folivo.trixnity.appservice.rest.user

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.user.RegisterResponse
import net.folivo.trixnity.core.model.MatrixId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class AppserviceUserServiceTest {

    class TestAppserviceUserService(override val matrixClient: MatrixClient<*>) : AppserviceUserService {

        override suspend fun userExistingState(userId: MatrixId.UserId): AppserviceUserService.UserExistingState {
            throw RuntimeException("this is not tested")
        }

        override suspend fun getRegisterUserParameter(userId: MatrixId.UserId): RegisterUserParameter {
            throw RuntimeException("this is not tested")
        }

        override suspend fun onRegisteredUser(userId: MatrixId.UserId) {
            throw RuntimeException("this is not tested")
        }
    }

    private val matrixClientMock: MatrixClient<*> = mockk()
    private val cut = spyk(TestAppserviceUserService(matrixClientMock))

    @BeforeTest
    fun beforeEach() {
        coEvery { cut.userExistingState(allAny()) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)
        coEvery { cut.getRegisterUserParameter(allAny()) }
            .returns(RegisterUserParameter())
        coEvery { cut.onRegisteredUser(allAny()) } just Runs
    }

    @Test
    fun `should create and save user`() {
        coEvery { cut.getRegisterUserParameter(MatrixId.UserId("user", "server")) }
            .returns(RegisterUserParameter("someDisplayName"))
        coEvery { matrixClientMock.user.register(allAny()) }
            .returns(RegisterResponse(MatrixId.UserId("user", "server")))
        coEvery { matrixClientMock.user.setDisplayName(allAny()) } just Runs

        runBlocking { cut.registerManagedUser(MatrixId.UserId("user", "server")) }

        coVerify {
            matrixClientMock.user.register(
                authenticationType = "m.login.application_service",
                username = "user"
            )
            matrixClientMock.user.setDisplayName(
                MatrixId.UserId("user", "server"),
                "someDisplayName",
                MatrixId.UserId("user", "server")
            )
            cut.onRegisteredUser(MatrixId.UserId("user", "server"))
        }
    }

    @Test
    fun `should have error when register fails`() {
        coEvery { cut.userExistingState(MatrixId.UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)

        coEvery { matrixClientMock.user.register(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("500", "M_UNKNOWN")
                )
            )

        try {
            runBlocking { cut.registerManagedUser(MatrixId.UserId("user", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }

        coVerify(exactly = 0) { cut.onRegisteredUser(any()) }
    }

    @Test
    fun `should catch error when register fails due to already existing id`() {
        coEvery { cut.getRegisterUserParameter(MatrixId.UserId("user", "server")) }
            .returns(RegisterUserParameter("someDisplayName"))
        coEvery { matrixClientMock.user.register(allAny()) }
            .returns(RegisterResponse(MatrixId.UserId("user", "server")))
        coEvery { matrixClientMock.user.setDisplayName(allAny()) } just Runs

        coEvery { matrixClientMock.user.register(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("M_USER_IN_USE", "Desired user ID is already taken.")
                )
            )

        runBlocking {
            cut.registerManagedUser(MatrixId.UserId("user", "server"))
        }

        coVerify {
            matrixClientMock.user.register(
                authenticationType = "m.login.application_service",
                username = "user"
            )
            matrixClientMock.user.setDisplayName(
                MatrixId.UserId("user", "server"),
                "someDisplayName",
                MatrixId.UserId("user", "server")
            )
            cut.onRegisteredUser(MatrixId.UserId("user", "server"))
        }
    }

    @Test
    fun `should have error when saving by user service fails`() {
        coEvery { cut.onRegisteredUser(MatrixId.UserId("user", "server")) }
            .throws(RuntimeException())
        coEvery { cut.getRegisterUserParameter(MatrixId.UserId("user", "server")) }
            .returns(RegisterUserParameter(displayName = "someDisplayName"))

        coEvery { matrixClientMock.user.setDisplayName(allAny()) } just Runs
        coEvery { matrixClientMock.user.register(allAny()) }
            .returns(RegisterResponse(MatrixId.UserId("user", "server")))

        try {
            runBlocking { cut.registerManagedUser(MatrixId.UserId("user", "server")) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify {
            matrixClientMock.user.setDisplayName(
                MatrixId.UserId("user", "server"),
                displayName = "someDisplayName",
                asUserId = MatrixId.UserId("user", "server")
            )
        }
    }

    @Test
    fun `should not set displayName if null`() {
        coEvery { cut.getRegisterUserParameter(MatrixId.UserId("user", "server")) }
            .returns(RegisterUserParameter())
        coEvery { matrixClientMock.user.register(allAny()) }
            .returns(RegisterResponse(MatrixId.UserId("user", "server")))

        runBlocking { cut.registerManagedUser(MatrixId.UserId("user", "server")) }

        val user = matrixClientMock.user
        coVerify(exactly = 0) { user.setDisplayName(allAny()) }
    }

    @Test
    fun `should not have error when setting displayName fails`() {
        coEvery { matrixClientMock.user.setDisplayName(allAny()) }
            .throws(MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse("M_UNKNOWN")))
        coEvery { matrixClientMock.user.register(allAny()) }
            .returns(RegisterResponse(MatrixId.UserId("user", "server")))

        runBlocking { cut.registerManagedUser(MatrixId.UserId("user", "server")) }
    }
}