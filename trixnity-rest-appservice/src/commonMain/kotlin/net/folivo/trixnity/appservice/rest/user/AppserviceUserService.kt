package net.folivo.trixnity.appservice.rest.user

import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.MatrixId

interface AppserviceUserService {

    val matrixClient: MatrixClient<*>

    enum class UserExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun userExistingState(userId: MatrixId.UserId): UserExistingState
    suspend fun getRegisterUserParameter(userId: MatrixId.UserId): RegisterUserParameter
    suspend fun onRegisteredUser(userId: MatrixId.UserId)

    suspend fun registerManagedUser(userId: MatrixId.UserId) {
        try {
            matrixClient.user.register(
                authenticationType = "m.login.application_service",
                username = userId.localpart
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse.errorCode == "M_USER_IN_USE") {
                // TODO log!
            } else throw error
        }
        val displayName = getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixClient.user.setDisplayName(
                userId,
                displayName,
                asUserId = userId
            )
        }
        onRegisteredUser(userId)
    }
}