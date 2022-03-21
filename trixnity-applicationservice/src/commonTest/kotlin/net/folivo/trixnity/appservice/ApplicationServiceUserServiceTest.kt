package net.folivo.trixnity.appservice

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.appservice.ApplicationServiceUserService.UserExistingState.CAN_BE_CREATED
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetDisplayName
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationServiceUserServiceTest {

    private val json = createMatrixJson()
    private val mappings = createEventContentSerializerMappings()
    private val userId = UserId("user", "server")

    @Test
    fun `should create and save user`() = runTest {
        var setDisplayNameCalled = false
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) { requestBody ->
                assertSoftly(requestBody.request) {
                    it.type shouldBe "m.login.application_service"
                    it.username shouldBe "user"
                }
                ResponseWithUIA.Success(Register.Response(userId))
            }
            matrixJsonEndpoint(json, mappings, SetDisplayName(userId.e(), userId)) { requestBody ->
                assertSoftly(requestBody) {
                    it.displayName shouldBe "someDisplayName"
                }
                setDisplayNameCalled = true
            }
        })
        val cut = TestApplicationServiceUserService(api)

        cut.userExistingState = Result.success(CAN_BE_CREATED)
        cut.getRegisterUserParameter = Result.success(RegisterUserParameter("someDisplayName"))

        cut.registerManagedUser(userId)

        cut.getRegisterUserParameterCalled shouldBe userId
        setDisplayNameCalled shouldBe true
        cut.onRegisteredUserCalled shouldBe userId
    }

    @Test
    fun `should have error when register fails`() = runTest {
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) {
                throw MatrixServerException(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse.Unknown("internal server error")
                )
            }
        })
        val cut = TestApplicationServiceUserService(api)

        cut.userExistingState = Result.success(CAN_BE_CREATED)

        shouldThrow<MatrixServerException> {
            cut.registerManagedUser(userId)
        }

        cut.onRegisteredUserCalled shouldBe null
    }

    @Test
    fun `should catch error when register fails due to already existing id`() = runTest {
        var setDisplayNameCalled = false
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) {
                throw                    MatrixServerException(
                    HttpStatusCode.BadRequest,
                    ErrorResponse.UserInUse("Desired user ID is already taken.")
                )
            }
            matrixJsonEndpoint(json, mappings, SetDisplayName(userId.e(), userId)) { requestBody ->
                assertSoftly(requestBody) {
                    it.displayName shouldBe "someDisplayName"
                }
                setDisplayNameCalled = true
            }
        })
        val cut = TestApplicationServiceUserService(api)

        cut.getRegisterUserParameter = Result.success(RegisterUserParameter("someDisplayName"))

        cut.registerManagedUser(userId)

        setDisplayNameCalled shouldBe true
        cut.onRegisteredUserCalled shouldBe userId
    }

    @Test
    fun `should have error when saving by user service fails`() = runTest {
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) {
                ResponseWithUIA.Success(Register.Response(userId))
            }
            matrixJsonEndpoint(json, mappings, SetDisplayName(userId.e(), userId)) { }
        })
        val cut = TestApplicationServiceUserService(api)

        cut.onRegisteredUser = Result.failure(RuntimeException())
        cut.getRegisterUserParameter = Result.success(RegisterUserParameter(displayName = "someDisplayName"))

        shouldThrow<RuntimeException> {
            cut.registerManagedUser(userId)
        }
    }

    @Test
    fun `should not set displayName if null`() = runTest {
        var setDisplayNameCalled = false
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) {
                ResponseWithUIA.Success(Register.Response(userId))
            }
            matrixJsonEndpoint(json, mappings, SetDisplayName(userId, userId)) {
                setDisplayNameCalled = true
            }
        })
        val cut = TestApplicationServiceUserService(api)

        cut.getRegisterUserParameter = Result.success(RegisterUserParameter(displayName = null))

        cut.registerManagedUser(userId)

        setDisplayNameCalled shouldBe false
    }

    @Test
    fun `should not have error when setting displayName fails`() = runTest {
        var setDisplayNameCalled = false
        val api = MatrixClientServerApiClient(json = json, httpClientFactory = mockEngineFactory {
            matrixJsonEndpoint(json, mappings, Register()) {
                ResponseWithUIA.Success(Register.Response(userId))
            }
            matrixJsonEndpoint(json, mappings, SetDisplayName(userId.e(), userId)) {
                setDisplayNameCalled = true
                throw MatrixServerException(
                    HttpStatusCode.BadRequest,
                    ErrorResponse.Unknown()
                )
            }
        })
        val cut = TestApplicationServiceUserService(api)
        cut.getRegisterUserParameter = Result.success(RegisterUserParameter("someDisplayName"))

        cut.registerManagedUser(userId)
        setDisplayNameCalled shouldBe true
    }
}