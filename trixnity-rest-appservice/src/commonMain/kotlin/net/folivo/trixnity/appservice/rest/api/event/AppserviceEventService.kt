package net.folivo.trixnity.appservice.rest.api.event

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event

interface AppserviceEventService {

    enum class EventProcessingState {
        PROCESSED, NOT_PROCESSED
    }

    suspend fun eventProcessingState(
        tnxId: String,
        eventId: MatrixId.EventId
    ): EventProcessingState

    suspend fun onEventProcessed(tnxId: String, eventId: MatrixId.EventId)

    suspend fun processEvent(event: Event<*>)
}