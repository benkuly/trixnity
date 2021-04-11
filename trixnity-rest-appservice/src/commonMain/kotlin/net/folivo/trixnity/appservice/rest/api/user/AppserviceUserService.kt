package net.folivo.trixnity.appservice.rest.api.user

import net.folivo.trixnity.core.model.MatrixId

interface AppserviceUserService {

    enum class UserExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun userExistingState(userId: MatrixId.UserId): UserExistingState
    suspend fun getRegisterUserParameter(userId: MatrixId.UserId): RegisterUserParameter
    suspend fun onRegisteredUser(userId: MatrixId.UserId)
}