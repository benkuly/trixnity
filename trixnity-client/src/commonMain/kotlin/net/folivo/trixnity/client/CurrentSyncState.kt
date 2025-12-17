package net.folivo.trixnity.client

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class CurrentSyncState(currentSyncState: StateFlow<SyncState>) : StateFlow<SyncState> by currentSyncState {
    constructor(api: MatrixClientServerApiClient) : this(api.sync.currentSyncState)
}