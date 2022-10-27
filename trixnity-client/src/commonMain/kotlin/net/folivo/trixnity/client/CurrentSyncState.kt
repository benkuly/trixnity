package net.folivo.trixnity.client

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.clientserverapi.client.SyncState

class CurrentSyncState(currentSyncState: StateFlow<SyncState>) : StateFlow<SyncState> by currentSyncState