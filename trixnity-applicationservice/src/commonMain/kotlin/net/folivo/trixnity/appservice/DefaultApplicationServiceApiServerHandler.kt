package net.folivo.trixnity.appservice

import net.folivo.trixnity.applicationserviceapi.server.ApplicationServiceApiServerHandler
import net.folivo.trixnity.appservice.ApplicationServiceRoomService.RoomExistingState
import net.folivo.trixnity.appservice.ApplicationServiceUserService.UserExistingState
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

class DefaultApplicationServiceApiServerHandler(
    private val applicationServiceEventTxnService: ApplicationServiceEventTxnService,
    private val applicationServiceUserService: ApplicationServiceUserService,
    private val applicationServiceRoomService: ApplicationServiceRoomService,
) : ApplicationServiceApiServerHandler, EventEmitter() {

    override suspend fun addTransaction(tnxId: String, events: List<Event<*>>) {
        when (applicationServiceEventTxnService.eventTnxProcessingState(tnxId)) {
            ApplicationServiceEventTxnService.EventTnxProcessingState.NOT_PROCESSED -> {
                events.forEach { emitEvent(it) }
                applicationServiceEventTxnService.onEventTnxProcessed(tnxId)
            }
            ApplicationServiceEventTxnService.EventTnxProcessingState.PROCESSED -> {
            }
        }
    }

    override suspend fun hasUser(userId: UserId): Boolean {
        return when (applicationServiceUserService.userExistingState(userId)) {
            UserExistingState.EXISTS -> true
            UserExistingState.DOES_NOT_EXISTS -> false
            UserExistingState.CAN_BE_CREATED -> {
                applicationServiceUserService.registerManagedUser(userId)
                true
            }
        }
    }

    override suspend fun hasRoomAlias(roomAlias: RoomAliasId): Boolean {
        return when (applicationServiceRoomService.roomExistingState(roomAlias)) {
            RoomExistingState.EXISTS -> true
            RoomExistingState.DOES_NOT_EXISTS -> false
            RoomExistingState.CAN_BE_CREATED -> {
                applicationServiceRoomService.createManagedRoom(roomAlias)
                true
            }
        }
    }
}