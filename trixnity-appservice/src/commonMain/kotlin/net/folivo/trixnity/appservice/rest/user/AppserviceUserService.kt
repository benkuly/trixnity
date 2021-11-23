package net.folivo.trixnity.appservice.rest.user

import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.MatrixServerException
import net.folivo.trixnity.core.model.UserId

interface AppserviceUserService {

    val matrixApiClient: MatrixApiClient

    enum class UserExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun userExistingState(userId: UserId): UserExistingState
    suspend fun getRegisterUserParameter(userId: UserId): RegisterUserParameter
    suspend fun onRegisteredUser(userId: UserId)

    suspend fun registerManagedUser(userId: UserId) {
        try {
            matrixApiClient.authentication.register(
                isAppservice = true,
                username = userId.localpart
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse is ErrorResponse.UserInUse) {
                // TODO log!
            } else throw error
        }
        val displayName = getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixApiClient.users.setDisplayName(
                userId,
                displayName,
                asUserId = userId
            )
        }
        onRegisteredUser(userId)
    }
}