package net.folivo.trixnity.appservice.rest.api

import com.soywiz.klogger.Logger
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService
import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.core.model.MatrixId

class AppserviceHandlerHelper(
    private val matrixClient: MatrixClient<*>,
    private val appserviceUserService: AppserviceUserService,
    private val appserviceRoomService: net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService
) {

    companion object {
        private val LOG = Logger()
    }

    suspend fun registerManagedUser(userId: MatrixId.UserId) {
        LOG.debug { "try to register user" }
        try {
            matrixClient.user.register(
                authenticationType = "m.login.application_service",
                username = userId.localpart
            )
        } catch (error: MatrixServerException) {
            if (error.errorResponse.errorCode == "M_USER_IN_USE") {
                LOG.warn { "user $userId has already been created" }
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
        LOG.debug { "registered user" }
        appserviceUserService.onRegisteredUser(userId)
    }

    suspend fun createManagedRoom(roomAlias: MatrixId.RoomAliasId) {
        LOG.debug { "try to create room" }
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
        LOG.debug { "created room" }
        appserviceRoomService.onCreatedRoom(roomAlias, roomId)
    }
}