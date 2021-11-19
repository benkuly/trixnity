package net.folivo.trixnity.appservice.rest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.appservice.rest.event.AppserviceEventTnxService
import net.folivo.trixnity.appservice.rest.room.AppserviceRoomService
import net.folivo.trixnity.appservice.rest.room.AppserviceRoomService.RoomExistingState
import net.folivo.trixnity.appservice.rest.user.AppserviceUserService
import net.folivo.trixnity.appservice.rest.user.AppserviceUserService.UserExistingState
import net.folivo.trixnity.core.EventEmitter
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

class DefaultAppserviceService(
    private val appserviceEventTnxService: AppserviceEventTnxService,
    private val appserviceUserService: AppserviceUserService,
    private val appserviceRoomService: AppserviceRoomService,
) : AppserviceService, EventEmitter() {

    override suspend fun addTransactions(tnxId: String, events: Flow<Event<*>>) {
        when (appserviceEventTnxService.eventTnxProcessingState(tnxId)) {
            AppserviceEventTnxService.EventTnxProcessingState.NOT_PROCESSED -> {
                events.collect { emitEvent(it) }
                appserviceEventTnxService.onEventTnxProcessed(tnxId)
            }
            AppserviceEventTnxService.EventTnxProcessingState.PROCESSED -> {
            }
        }
    }

    override suspend fun hasUser(userId: UserId): Boolean {
        return when (appserviceUserService.userExistingState(userId)) {
            UserExistingState.EXISTS -> true
            UserExistingState.DOES_NOT_EXISTS -> false
            UserExistingState.CAN_BE_CREATED -> {
                appserviceUserService.registerManagedUser(userId)
                true
            }
        }
    }

    override suspend fun hasRoomAlias(roomAlias: RoomAliasId): Boolean {
        return when (appserviceRoomService.roomExistingState(roomAlias)) {
            RoomExistingState.EXISTS -> true
            RoomExistingState.DOES_NOT_EXISTS -> false
            RoomExistingState.CAN_BE_CREATED -> {
                appserviceRoomService.createManagedRoom(roomAlias)
                true
            }
        }
    }
}