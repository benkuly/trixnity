package net.folivo.trixnity.appservice.rest.api

import com.soywiz.klogger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService.RoomExistingState
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService
import net.folivo.trixnity.appservice.rest.api.user.AppserviceUserService.UserExistingState
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event

class DefaultAppserviceHandler(
    private val appserviceEventService: net.folivo.trixnity.appservice.rest.api.event.AppserviceEventService,
    private val appserviceUserService: AppserviceUserService,
    private val appserviceRoomService: net.folivo.trixnity.appservice.rest.api.room.AppserviceRoomService,
    private val helper: AppserviceHandlerHelper
) : AppserviceHandler {

    companion object {
        private val LOG = Logger()
    }

    override suspend fun addTransactions(tnxId: String, events: Flow<Event<*>>) {
        try {
            events.collect { event ->
                val eventId = when (event) {
                    is Event.RoomEvent -> event.id
                    is Event.StateEvent -> event.id
                    else -> null
                }
                LOG.debug { "incoming event $eventId in transaction $tnxId" }
                if (eventId == null) {
                    LOG.debug { "process event $eventId in transaction $tnxId" }
                    appserviceEventService.processEvent(event)
                } else when (appserviceEventService.eventProcessingState(tnxId, eventId)) {
                    net.folivo.trixnity.appservice.rest.api.event.AppserviceEventService.EventProcessingState.NOT_PROCESSED -> {
                        LOG.debug { "process event $eventId in transaction $tnxId" }
                        appserviceEventService.processEvent(event)
                        appserviceEventService.onEventProcessed(
                            tnxId,
                            eventId
                        )
                    }
                    net.folivo.trixnity.appservice.rest.api.event.AppserviceEventService.EventProcessingState.PROCESSED -> {
                        LOG.debug { "event $eventId in transaction $tnxId already processed" }
                    }
                }
            }
        } catch (error: Throwable) {
            LOG.error { "something went wrong while processing events: ${error.stackTraceToString()}" }
            throw error
        }
    }

    override suspend fun hasUser(userId: MatrixId.UserId): Boolean {
        LOG.debug { "handle has user" }
        return when (appserviceUserService.userExistingState(userId)) {
            UserExistingState.EXISTS -> true
            UserExistingState.DOES_NOT_EXISTS -> false
            UserExistingState.CAN_BE_CREATED -> {
                LOG.debug { "started user creation of $userId" }
                helper.registerManagedUser(userId)
                true
            }
        }
    }

    override suspend fun hasRoomAlias(roomAlias: MatrixId.RoomAliasId): Boolean {
        LOG.debug { "handle has room alias" }
        return when (appserviceRoomService.roomExistingState(roomAlias)) {
            RoomExistingState.EXISTS -> true
            RoomExistingState.DOES_NOT_EXISTS -> false
            RoomExistingState.CAN_BE_CREATED -> {
                LOG.debug { "started room creation of $roomAlias" }
                helper.createManagedRoom(roomAlias)
                true
            }
        }
    }
}