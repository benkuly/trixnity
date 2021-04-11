package net.folivo.trixnity.appservice.rest.api.room

import net.folivo.trixnity.core.model.MatrixId

interface AppserviceRoomService {

    enum class RoomExistingState {
        EXISTS, DOES_NOT_EXISTS, CAN_BE_CREATED
    }

    suspend fun roomExistingState(roomAlias: MatrixId.RoomAliasId): RoomExistingState
    suspend fun getCreateRoomParameter(roomAlias: MatrixId.RoomAliasId): CreateRoomParameter
    suspend fun onCreatedRoom(roomAlias: MatrixId.RoomAliasId, roomId: MatrixId.RoomId)
}