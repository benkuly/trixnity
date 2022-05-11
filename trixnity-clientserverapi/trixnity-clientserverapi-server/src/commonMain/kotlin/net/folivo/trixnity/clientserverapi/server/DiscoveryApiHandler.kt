package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown

interface DiscoveryApiHandler {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, DiscoveryInformation>): DiscoveryInformation
}