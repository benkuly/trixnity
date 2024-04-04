package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetSupport
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown

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