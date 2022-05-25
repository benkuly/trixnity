package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.sync.Sync

interface SyncApiHandler {
    /**
     * @see [Sync]
     */
    suspend fun sync(context: MatrixEndpointContext<Sync, Unit, Sync.Response>): Sync.Response
}