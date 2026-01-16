package de.connect2x.trixnity.client

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncState

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class CurrentSyncState(currentSyncState: StateFlow<SyncState>) : StateFlow<SyncState> by currentSyncState {
    constructor(api: MatrixClientServerApiClient) : this(api.sync.currentSyncState)
}