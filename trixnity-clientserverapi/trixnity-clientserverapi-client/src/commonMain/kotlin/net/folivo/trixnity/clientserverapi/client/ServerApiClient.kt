package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.core.model.UserId

interface IServerApiClient {
    /**
     * @see [GetVersions]
     */
    suspend fun getVersions(): Result<GetVersions.Response>

    /**
     * @see [GetCapabilities]
     */
    suspend fun getCapabilities(): Result<GetCapabilities.Response>

    /**
     * @see [Search]
     */
    suspend fun search(
        request: Search.Request,
        nextBatch: String? = null,
        asUserId: UserId? = null
    ): Result<Search.Response>

    /**
     * @see [WhoIs]
     */
    suspend fun whoIs(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<WhoIs.Response>
}

class ServerApiClient(private val httpClient: MatrixClientServerApiHttpClient) : IServerApiClient {

    override suspend fun getVersions(): Result<GetVersions.Response> =
        httpClient.request(GetVersions)

    override suspend fun getCapabilities(): Result<GetCapabilities.Response> =
        httpClient.request(GetCapabilities)

    override suspend fun search(
        request: Search.Request,
        nextBatch: String?,
        asUserId: UserId?
    ): Result<Search.Response> =
        httpClient.request(Search(nextBatch, asUserId), request)

    override suspend fun whoIs(
        userId: UserId,
        asUserId: UserId?
    ): Result<WhoIs.Response> =
        httpClient.request(WhoIs(userId, asUserId))
}