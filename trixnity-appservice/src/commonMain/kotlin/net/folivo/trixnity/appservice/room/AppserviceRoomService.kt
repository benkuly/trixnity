package net.folivo.trixnity.appservice.room

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId

interface AppserviceRoomService {

    val matrixClientServerApiClient: MatrixClientServerApiClient

    enum class RoomExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun roomExistingState(roomAlias: RoomAliasId): RoomExistingState
    suspend fun getCreateRoomParameter(roomAlias: RoomAliasId): CreateRoomParameter
    suspend fun onCreatedRoom(roomAlias: RoomAliasId, roomId: RoomId)

    suspend fun createManagedRoom(roomAlias: RoomAliasId) {
        val createRoomParameter = getCreateRoomParameter(roomAlias)
        val roomId = matrixClientServerApiClient.rooms
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
            ).getOrThrow()
        onCreatedRoom(roomAlias, roomId)
    }
}