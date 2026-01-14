package net.folivo.trixnity.appservice

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.appservice.ApplicationServiceUserService.UserExistingState
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.users.ProfileField
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger("net.folivo.trixnity.appservice.ApplicationServiceUserService")

interface ApplicationServiceUserService {

    val matrixClientServerApiClient: MatrixClientServerApiClient

    enum class UserExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun userExistingState(userId: UserId): UserExistingState
    suspend fun getRegisterUserParameter(userId: UserId): RegisterUserParameter
    suspend fun onRegisteredUser(userId: UserId)

    suspend fun registerManagedUser(userId: UserId) {
        try {
            matrixClientServerApiClient.authentication.register(
                isAppservice = true,
                username = userId.localpart
            ).getOrThrow()
        } catch (error: MatrixServerException) {
            if (error.errorResponse is ErrorResponse.UserInUse) {
                log.error { "user $userId already in use" }
            } else throw error
        }
        val displayName = getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixClientServerApiClient.user.setProfileField(
                userId,
                ProfileField.DisplayName(displayName),
                asUserId = userId
            ).onFailure {
                log.error(it) { "could not set displayname of $userId to $displayName" }
            }
        }
        onRegisteredUser(userId)
    }
}