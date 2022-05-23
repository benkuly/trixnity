package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown

interface DiscoveryApiHandler {

    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, DiscoveryInformation>): DiscoveryInformation
}