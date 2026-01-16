package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import de.connect2x.trixnity.clientserverapi.model.discovery.GetSupport
import de.connect2x.trixnity.clientserverapi.model.discovery.GetWellKnown

interface DiscoveryApiHandler {

    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, DiscoveryInformation>): DiscoveryInformation

    /**
     * @see [GetSupport]
     */
    suspend fun getSupport(context: MatrixEndpointContext<GetSupport, Unit, GetSupport.Response>): GetSupport.Response

}