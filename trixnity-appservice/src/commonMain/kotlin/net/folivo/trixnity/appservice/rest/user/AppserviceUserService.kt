package net.folivo.trixnity.appservice.rest.user

import net.folivo.trixnity.client.rest.MatrixRestClient
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.MatrixId

interface AppserviceUserService {

    val matrixRestClient: MatrixRestClient

    enum class UserExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun userExistingState(userId: MatrixId.UserId): UserExistingState
    suspend fun getRegisterUserParameter(userId: MatrixId.UserId): RegisterUserParameter
    suspend fun onRegisteredUser(userId: MatrixId.UserId)

    suspend fun registerManagedUser(userId: MatrixId.UserId) {
        try {
            matrixRestClient.user.register(
                isAppservice = true,
                username = userId.localpart
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse.errorCode == "M_USER_IN_USE") {
                // TODO log!
            } else throw error
        }
        val displayName = getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixRestClient.user.setDisplayName(
                userId,
                displayName,
                asUserId = userId
            )
        }
        onRegisteredUser(userId)
    }
}