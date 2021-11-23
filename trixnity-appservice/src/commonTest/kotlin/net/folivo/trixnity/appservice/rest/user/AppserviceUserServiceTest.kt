package net.folivo.trixnity.appservice.rest.user

import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.MatrixServerException
import net.folivo.trixnity.client.api.authentication.RegisterResponse
import net.folivo.trixnity.client.api.uia.UIA
import net.folivo.trixnity.core.model.UserId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

class AppserviceUserServiceTest {

    class TestAppserviceUserService(override val matrixApiClient: MatrixApiClient) : AppserviceUserService {

        override suspend fun userExistingState(userId: UserId): AppserviceUserService.UserExistingState {
            throw RuntimeException("this is not tested")
        }

        override suspend fun getRegisterUserParameter(userId: UserId): RegisterUserParameter {
            throw RuntimeException("this is not tested")
        }

        override suspend fun onRegisteredUser(userId: UserId) {
            throw RuntimeException("this is not tested")
        }
    }

    private val matrixApiClientMock: MatrixApiClient = mockk()
    private val cut = spyk(TestAppserviceUserService(matrixApiClientMock))

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
        coEvery { cut.getRegisterUserParameter(UserId("user", "server")) }
            .returns(RegisterUserParameter("someDisplayName"))
        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .returns(UIA.UIASuccess(RegisterResponse(UserId("user", "server"))))
        coEvery { matrixApiClientMock.users.setDisplayName(allAny()) } just Runs

        runBlocking { cut.registerManagedUser(UserId("user", "server")) }

        coVerify {
            matrixApiClientMock.authentication.register(
                isAppservice = true,
                username = "user"
            )
            matrixApiClientMock.users.setDisplayName(
                UserId("user", "server"),
                "someDisplayName",
                UserId("user", "server")
            )
            cut.onRegisteredUser(UserId("user", "server"))
        }
    }

    @Test
    fun `should have error when register fails`() {
        coEvery { cut.userExistingState(UserId("user", "server")) }
            .returns(AppserviceUserService.UserExistingState.CAN_BE_CREATED)

        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse.Unknown("internal server error")
                )
            )

        try {
            runBlocking { cut.registerManagedUser(UserId("user", "server")) }
            fail("should have error")
        } catch (error: Throwable) {

        }

        coVerify(exactly = 0) { cut.onRegisteredUser(any()) }
    }

    @Test
    fun `should catch error when register fails due to already existing id`() {
        coEvery { cut.getRegisterUserParameter(UserId("user", "server")) }
            .returns(RegisterUserParameter("someDisplayName"))
        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .returns(UIA.UIASuccess(RegisterResponse(UserId("user", "server"))))
        coEvery { matrixApiClientMock.users.setDisplayName(allAny()) } just Runs

        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .throws(
                MatrixServerException(
                    HttpStatusCode.BadRequest,
                    ErrorResponse.UserInUse("Desired user ID is already taken.")
                )
            )

        runBlocking {
            cut.registerManagedUser(UserId("user", "server"))
        }

        coVerify {
            matrixApiClientMock.authentication.register(
                isAppservice = true,
                username = "user"
            )
            matrixApiClientMock.users.setDisplayName(
                UserId("user", "server"),
                "someDisplayName",
                UserId("user", "server")
            )
            cut.onRegisteredUser(UserId("user", "server"))
        }
    }

    @Test
    fun `should have error when saving by user service fails`() {
        coEvery { cut.onRegisteredUser(UserId("user", "server")) }
            .throws(RuntimeException())
        coEvery { cut.getRegisterUserParameter(UserId("user", "server")) }
            .returns(RegisterUserParameter(displayName = "someDisplayName"))

        coEvery { matrixApiClientMock.users.setDisplayName(allAny()) } just Runs
        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .returns(UIA.UIASuccess(RegisterResponse(UserId("user", "server"))))

        try {
            runBlocking { cut.registerManagedUser(UserId("user", "server")) }
            fail("should have error")
        } catch (error: Throwable) {
        }

        coVerify {
            matrixApiClientMock.users.setDisplayName(
                UserId("user", "server"),
                displayName = "someDisplayName",
                asUserId = UserId("user", "server")
            )
        }
    }

    @Test
    fun `should not set displayName if null`() {
        coEvery { cut.getRegisterUserParameter(UserId("user", "server")) }
            .returns(RegisterUserParameter())
        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .returns(UIA.UIASuccess(RegisterResponse(UserId("user", "server"))))
        coEvery { matrixApiClientMock.users.setDisplayName(allAny()) } just Runs

        runBlocking { cut.registerManagedUser(UserId("user", "server")) }

        val user = matrixApiClientMock.users
        coVerify(exactly = 0) { user.setDisplayName(allAny()) }
    }

    @Test
    fun `should not have error when setting displayName fails`() {
        coEvery { matrixApiClientMock.users.setDisplayName(allAny()) }
            .throws(MatrixServerException(HttpStatusCode.BadRequest, ErrorResponse.Unknown()))
        coEvery { matrixApiClientMock.authentication.register(allAny()) }
            .returns(UIA.UIASuccess(RegisterResponse(UserId("user", "server"))))

        runBlocking { cut.registerManagedUser(UserId("user", "server")) }
    }
}