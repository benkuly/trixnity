package net.folivo.trixnity.appservice.user

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.core.model.UserId

interface AppserviceUserService {

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
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse is net.folivo.trixnity.clientserverapi.model.ErrorResponse.UserInUse) {
                // TODO log!
            } else throw error
        }
        val displayName = getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixClientServerApiClient.users.setDisplayName(
                userId,
                displayName,
                asUserId = userId
            )
        }
        onRegisteredUser(userId)
    }
}