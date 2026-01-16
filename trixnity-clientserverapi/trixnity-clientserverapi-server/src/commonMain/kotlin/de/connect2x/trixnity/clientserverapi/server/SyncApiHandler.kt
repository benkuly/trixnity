package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.sync.Sync

interface SyncApiHandler {
    /**
     * @see [Sync]
     */
    suspend fun sync(context: MatrixEndpointContext<Sync, Unit, Sync.Response>): Sync.Response
}