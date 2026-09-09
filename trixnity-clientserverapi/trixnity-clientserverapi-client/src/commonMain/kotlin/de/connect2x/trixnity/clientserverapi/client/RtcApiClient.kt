package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.rtc.GetTransports
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport

@MSC4143
interface RtcApiClient {
    /** @see [GetTransports] */
    suspend fun getTransports(): Result<List<RtcTransport>>
}

@MSC4143
class RtcApiClientImpl(private val baseClient: MatrixClientServerApiBaseClient) : RtcApiClient {
    override suspend fun getTransports(): Result<List<RtcTransport>> =
        baseClient.request(GetTransports).map { it.transports }
}
