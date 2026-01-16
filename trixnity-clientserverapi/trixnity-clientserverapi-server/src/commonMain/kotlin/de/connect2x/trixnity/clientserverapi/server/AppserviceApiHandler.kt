package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.appservice.Ping

interface AppserviceApiHandler {

    /**
     * @see [Ping]
     */
    suspend fun ping(context: MatrixEndpointContext<Ping, Ping.Request, Ping.Response>): Ping.Response
}