package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.appservice.Ping

interface AppserviceApiHandler {

    /**
     * @see [Ping]
     */
    suspend fun ping(context: MatrixEndpointContext<Ping, Ping.Request, Ping.Response>): Ping.Response
}