package net.folivo.trixnity.appservice

import net.folivo.trixnity.applicationserviceapi.server.ApplicationServiceApiServerHandler
import net.folivo.trixnity.appservice.ApplicationServiceRoomService.RoomExistingState
import net.folivo.trixnity.appservice.ApplicationServiceUserService.UserExistingState
import net.folivo.trixnity.core.EventEmitterImpl
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

class DefaultApplicationServiceApiServerHandler(
    private val applicationServiceEventTxnService: ApplicationServiceEventTxnService,
    private val applicationServiceUserService: ApplicationServiceUserService,
    private val applicationServiceRoomService: ApplicationServiceRoomService,
) : ApplicationServiceApiServerHandler, EventEmitterImpl() {

    override suspend fun addTransaction(txnId: String, events: List<Event<*>>) {
        when (applicationServiceEventTxnService.eventTnxProcessingState(txnId)) {
            ApplicationServiceEventTxnService.EventTnxProcessingState.NOT_PROCESSED -> {
                events.forEach { emitEvent(it) }
                applicationServiceEventTxnService.onEventTnxProcessed(txnId)
            }

            ApplicationServiceEventTxnService.EventTnxProcessingState.PROCESSED -> {
            }
        }
    }

    override suspend fun hasUser(userId: UserId) {
        when (applicationServiceUserService.userExistingState(userId)) {
            UserExistingState.EXISTS -> {}
            UserExistingState.DOES_NOT_EXISTS -> throw MatrixNotFoundException("user $userId not found")
            UserExistingState.CAN_BE_CREATED -> {
                applicationServiceUserService.registerManagedUser(userId)
            }
        }
    }

    override suspend fun hasRoomAlias(roomAlias: RoomAliasId) {
        when (applicationServiceRoomService.roomExistingState(roomAlias)) {
            RoomExistingState.EXISTS -> {}
            RoomExistingState.DOES_NOT_EXISTS -> throw MatrixNotFoundException("room $roomAlias not found")
            RoomExistingState.CAN_BE_CREATED -> {
                applicationServiceRoomService.createManagedRoom(roomAlias)
            }
        }
    }

    override suspend fun ping(txnId: String?) {
        // TODO
    }
}