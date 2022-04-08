package net.folivo.trixnity.appservice

interface ApplicationServiceEventTxnService {

    enum class EventTnxProcessingState {
        PROCESSED, NOT_PROCESSED
    }

    suspend fun eventTnxProcessingState(txnId: String): EventTnxProcessingState

    suspend fun onEventTnxProcessed(txnId: String)
}