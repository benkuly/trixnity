package net.folivo.trixnity.appservice.event

interface AppserviceEventTnxService {

    enum class EventTnxProcessingState {
        PROCESSED, NOT_PROCESSED
    }

    suspend fun eventTnxProcessingState(tnxId: String): EventTnxProcessingState

    suspend fun onEventTnxProcessed(tnxId: String)
}