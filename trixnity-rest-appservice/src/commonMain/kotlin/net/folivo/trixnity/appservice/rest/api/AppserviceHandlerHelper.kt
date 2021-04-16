package net.folivo.trixnity.appservice.rest.api

import net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.MatrixId

class AppserviceHandlerHelper(
    private val matrixClient: MatrixClient<*>,
    private val appserviceUserService: AppserviceUserService,
    private val appserviceRoomService: AppserviceRoomService
) {

    suspend fun registerManagedUser(userId: MatrixId.UserId) {
        try {
            matrixClient.user.register(
                authenticationType = "m.login.application_service",
                username = userId.localpart
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse.errorCode == "M_USER_IN_USE") {
            } else throw error
        }
        val displayName = appserviceUserService.getRegisterUserParameter(userId).displayName
        if (displayName != null) {
            matrixClient.user.setDisplayName(
                userId,
                displayName,
                asUserId = userId
            )
        }
        appserviceUserService.onRegisteredUser(userId)
    }

    suspend fun createManagedRoom(roomAlias: MatrixId.RoomAliasId) {
        val createRoomParameter = appserviceRoomService.getCreateRoomParameter(roomAlias)
        val roomId = matrixClient.room
            .createRoom(
                roomAliasId = roomAlias,
                visibility = createRoomParameter.visibility,
                name = createRoomParameter.name,
                topic = createRoomParameter.topic,
                invite = createRoomParameter.invite,
                invite3Pid = createRoomParameter.invite3Pid,
                roomVersion = createRoomParameter.roomVersion,
                asUserId = createRoomParameter.asUserId,
                creationContent = createRoomParameter.creationContent,
                initialState = createRoomParameter.initialState,
                isDirect = createRoomParameter.isDirect,
                powerLevelContentOverride = createRoomParameter.powerLevelContentOverride,
                preset = createRoomParameter.preset
            )
        appserviceRoomService.onCreatedRoom(roomAlias, roomId)
    }
}