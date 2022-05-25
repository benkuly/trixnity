package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown

interface IDiscoveryApiClient {
    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(): Result<DiscoveryInformation>
}

class DiscoveryApiClient(
    private val httpClient: MatrixClientServerApiHttpClient
) : IDiscoveryApiClient {

    override suspend fun getWellKnown(): Result<DiscoveryInformation> =
        httpClient.request(GetWellKnown)
}