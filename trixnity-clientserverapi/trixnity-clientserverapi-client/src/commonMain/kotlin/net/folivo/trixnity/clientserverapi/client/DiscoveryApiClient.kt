package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetSupport
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown

interface DiscoveryApiClient {
    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(): Result<DiscoveryInformation>

    /**
     * @see [GetSupport]
     */
    suspend fun getSupport(): Result<GetSupport.Response>
}

class DiscoveryApiClientImpl(
    private val httpClient: MatrixClientServerApiHttpClient
) : DiscoveryApiClient {

    override suspend fun getWellKnown(): Result<DiscoveryInformation> =
        httpClient.request(GetWellKnown)

    override suspend fun getSupport(): Result<GetSupport.Response> =
        httpClient.request(GetSupport)
}