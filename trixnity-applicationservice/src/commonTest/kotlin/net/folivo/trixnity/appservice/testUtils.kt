package net.folivo.trixnity.appservice

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

class TestApplicationServiceRoomService(override val matrixClientServerApiClient: MatrixClientServerApiClient) :
    ApplicationServiceRoomService {
    var roomExistingStateCalled: RoomAliasId? = null
    lateinit var roomExistingState: ApplicationServiceRoomService.RoomExistingState
    override suspend fun roomExistingState(roomAlias: RoomAliasId): ApplicationServiceRoomService.RoomExistingState {
        roomExistingStateCalled = roomAlias
        return roomExistingState
    }

    var createRoomParameterCalled: RoomAliasId? = null
    lateinit var createRoomParameter: CreateRoomParameter
    override suspend fun getCreateRoomParameter(roomAlias: RoomAliasId): CreateRoomParameter {
        createRoomParameterCalled = roomAlias
        return createRoomParameter
    }

    var onCreateRoomCalled: Pair<RoomAliasId, RoomId>? = null
    var onCreateRoom: Result<Unit> = Result.success(Unit)
    override suspend fun onCreatedRoom(roomAlias: RoomAliasId, roomId: RoomId) {
        onCreateRoomCalled = roomAlias to roomId
        onCreateRoom.getOrThrow()
    }
}

class TestApplicationServiceUserService(override val matrixClientServerApiClient: MatrixClientServerApiClient) :
    ApplicationServiceUserService {

    var userExistingState: Result<ApplicationServiceUserService.UserExistingState> =
        Result.failure(NotImplementedError())
    var userExistingStateCalled: UserId? = null
    override suspend fun userExistingState(userId: UserId): ApplicationServiceUserService.UserExistingState {
        userExistingStateCalled = userId
        return userExistingState.getOrThrow()
    }

    var getRegisterUserParameter: Result<RegisterUserParameter> = Result.failure(NotImplementedError())
    var getRegisterUserParameterCalled: UserId? = null
    override suspend fun getRegisterUserParameter(userId: UserId): RegisterUserParameter {
        getRegisterUserParameterCalled = userId
        return getRegisterUserParameter.getOrThrow()
    }

    var onRegisteredUser: Result<Unit> = Result.success(Unit)
    var onRegisteredUserCalled: UserId? = null
    override suspend fun onRegisteredUser(userId: UserId) {
        onRegisteredUserCalled = userId
        onRegisteredUser.getOrThrow()
    }
}

class TestApplicationServiceEventTxnService :
    ApplicationServiceEventTxnService {
    var eventTnxProcessingState: Result<ApplicationServiceEventTxnService.EventTnxProcessingState> =
        Result.failure(NotImplementedError())
    var eventTnxProcessingStateCalled: String? = null

    override suspend fun eventTnxProcessingState(txnId: String): ApplicationServiceEventTxnService.EventTnxProcessingState {
        eventTnxProcessingStateCalled = txnId
        return eventTnxProcessingState.getOrThrow()
    }

    var onEventTnxProcessed: Result<Unit> = Result.success(Unit)
    var onEventTnxProcessedCalled: String? = null
    override suspend fun onEventTnxProcessed(txnId: String) {
        onEventTnxProcessedCalled = txnId
        onEventTnxProcessed.getOrThrow()
    }

}