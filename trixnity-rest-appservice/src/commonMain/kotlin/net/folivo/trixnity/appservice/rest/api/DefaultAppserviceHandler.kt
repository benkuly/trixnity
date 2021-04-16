package net.folivo.trixnity.appservice.rest.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.appservice.rest.api.event.AppserviceEventService
import net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService.RoomExistingState
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService.UserExistingState
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event

class DefaultAppserviceHandler(
    private val appserviceEventService: AppserviceEventService,
    private val appserviceUserService: AppserviceUserService,
    private val appserviceRoomService: net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService,
    private val helper: AppserviceHandlerHelper
) : AppserviceHandler {

    override suspend fun addTransactions(tnxId: String, events: Flow<Event<*>>) {
        try {
            events.collect { event ->
                val eventId = when (event) {
                    is Event.RoomEvent -> event.id
                    is Event.StateEvent -> event.id
                    else -> null
                }
                if (eventId == null) {
                    appserviceEventService.processEvent(event)
                } else when (appserviceEventService.eventProcessingState(tnxId, eventId)) {
                    AppserviceEventService.EventProcessingState.NOT_PROCESSED -> {
                        appserviceEventService.processEvent(event)
                        appserviceEventService.onEventProcessed(
                            tnxId,
                            eventId
                        )
                    }
                    AppserviceEventService.EventProcessingState.PROCESSED -> {
                    }
                }
            }
        } catch (error: Throwable) {
            throw error
        }
    }

    override suspend fun hasUser(userId: MatrixId.UserId): Boolean {
        return when (appserviceUserService.userExistingState(userId)) {
            UserExistingState.EXISTS -> true
            UserExistingState.DOES_NOT_EXISTS -> false
            UserExistingState.CAN_BE_CREATED -> {
                helper.registerManagedUser(userId)
                true
            }
        }
    }

    override suspend fun hasRoomAlias(roomAlias: MatrixId.RoomAliasId): Boolean {
        return when (appserviceRoomService.roomExistingState(roomAlias)) {
            RoomExistingState.EXISTS -> true
            RoomExistingState.DOES_NOT_EXISTS -> false
            RoomExistingState.CAN_BE_CREATED -> {
                helper.createManagedRoom(roomAlias)
                true
            }
        }
    }
}