package net.folivo.trixnity.appservice

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.*
import net.folivo.trixnity.appservice.ApplicationServiceUserService.UserExistingState.CAN_BE_CREATED
import net.folivo.trixnity.clientserverapi.client.*
import net.folivo.trixnity.clientserverapi.model.authentication.LoginType
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.ProfileField
import net.folivo.trixnity.clientserverapi.model.users.SetProfileField
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.testutils.scopedMockEngineWithEndpoints
import kotlin.test.Test

class ApplicationServiceUserServiceTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()
    private val mappings = createDefaultEventContentSerializerMappings()
    private val userId = UserId("user", "server")

    private val baseUrl = Url("https://matrix.host")

    private val classicAuthProviderStore =
        MatrixClientAuthProviderDataStore.inMemory(MatrixClientAuthProviderData.classic(baseUrl, "access")).apply {
            scheduleSetup {
                setAuthData(MatrixClientAuthProviderData.classic(baseUrl, "access"))
            }
        }
    private val classicAuthProvider =
        ClassicMatrixClientAuthProvider(baseUrl, classicAuthProviderStore, {})

    @Test
    fun `should create and save user`() = runTest {
        var setDisplayNameCalled = false
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) { requestBody ->
                    assertSoftly(requestBody.request) {
                        it.type shouldBe LoginType.AppService
                        it.username shouldBe "user"
                    }
                    ResponseWithUIA.Success(Register.Response(userId))
                }
                matrixJsonEndpoint(SetProfileField(userId, ProfileField.DisplayName, userId)) { requestBody ->
                    assertSoftly(requestBody) {
                        it.shouldBeInstanceOf<ProfileField.DisplayName>().value shouldBe "someDisplayName"
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
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
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
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    throw MatrixServerException(
                        HttpStatusCode.BadRequest,
                        ErrorResponse.UserInUse("Desired user ID is already taken.")
                    )
                }
                matrixJsonEndpoint(SetProfileField(userId, ProfileField.DisplayName, userId)) { requestBody ->
                    assertSoftly(requestBody) {
                        it.shouldBeInstanceOf<ProfileField.DisplayName>().value shouldBe "someDisplayName"
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
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    ResponseWithUIA.Success(Register.Response(userId))
                }
                matrixJsonEndpoint(SetProfileField(userId, ProfileField.DisplayName, userId)) { }
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
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    ResponseWithUIA.Success(Register.Response(userId))
                }
                matrixJsonEndpoint(SetProfileField(userId, ProfileField.DisplayName, userId)) {
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
        val api = MatrixClientServerApiClientImpl(
            authProvider = classicAuthProvider,
            json = json,
            httpClientEngine = scopedMockEngineWithEndpoints(json, mappings) {
                matrixJsonEndpoint(Register()) {
                    ResponseWithUIA.Success(Register.Response(userId))
                }
                matrixJsonEndpoint(SetProfileField(userId, ProfileField.DisplayName, userId)) {
                    setDisplayNameCalled = true
                    throw MatrixServerException(
                        HttpStatusCode.BadRequest,
                        ErrorResponse.Unknown("")
                    )
                }
            })
        val cut = TestApplicationServiceUserService(api)
        cut.getRegisterUserParameter = Result.success(RegisterUserParameter("someDisplayName"))

        cut.registerManagedUser(userId)
        setDisplayNameCalled shouldBe true
    }
}