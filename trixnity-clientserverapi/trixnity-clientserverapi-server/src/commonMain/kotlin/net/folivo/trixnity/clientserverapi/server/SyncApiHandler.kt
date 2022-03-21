package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.sync.Sync

interface SyncApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3sync">matrix spec</a>
     */
    suspend fun sync(context: MatrixEndpointContext<Sync, Unit, Sync.Response>): Sync.Response
}