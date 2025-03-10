package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.appservice.Ping

interface AppserviceApiClient {
    /**
     * @see [Ping]
     */
    suspend fun ping(appserviceId: String, transactionId: String? = null): Result<Ping.Response>
}

class AppserviceApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : AppserviceApiClient {

    override suspend fun ping(appserviceId: String, transactionId: String?): Result<Ping.Response> =
        baseClient.request(Ping(appserviceId), Ping.Request(transactionId))
}