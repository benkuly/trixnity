package net.folivo.trixnity.appservice.rest.room

import net.folivo.trixnity.client.rest.MatrixClient
import net.folivo.trixnity.core.model.MatrixId

interface AppserviceRoomService {

    val matrixClient: MatrixClient<*>

    enum class RoomExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun roomExistingState(roomAlias: MatrixId.RoomAliasId): RoomExistingState
    suspend fun getCreateRoomParameter(roomAlias: MatrixId.RoomAliasId): CreateRoomParameter
    suspend fun onCreatedRoom(roomAlias: MatrixId.RoomAliasId, roomId: MatrixId.RoomId)

    suspend fun createManagedRoom(roomAlias: MatrixId.RoomAliasId) {
        val createRoomParameter = getCreateRoomParameter(roomAlias)
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
        onCreatedRoom(roomAlias, roomId)
    }
}