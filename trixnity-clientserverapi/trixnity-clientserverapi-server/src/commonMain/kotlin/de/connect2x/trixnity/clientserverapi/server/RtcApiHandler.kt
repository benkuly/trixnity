package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.core.MSC4143

@MSC4143
interface RtcApiHandler {
    /** @see [GetTransports] */
    suspend fun getTransports(
        context: MatrixEndpointContext<GetTransports, Unit, GetTransports.Response>
    ): GetTransports.Response
}
