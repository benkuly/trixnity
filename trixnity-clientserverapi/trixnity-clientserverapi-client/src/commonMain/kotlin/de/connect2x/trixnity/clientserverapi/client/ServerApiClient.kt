package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.server.GetCapabilities
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import de.connect2x.trixnity.clientserverapi.model.server.Search
import de.connect2x.trixnity.clientserverapi.model.server.WhoIs
import de.connect2x.trixnity.core.model.UserId

interface ServerApiClient {
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
    ): Result<Search.Response>

    /**
     * @see [WhoIs]
     */
    suspend fun whoIs(
        userId: UserId,
    ): Result<WhoIs.Response>
}

class ServerApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : ServerApiClient {

    override suspend fun getVersions(): Result<GetVersions.Response> =
        baseClient.request(GetVersions)

    override suspend fun getCapabilities(): Result<GetCapabilities.Response> =
        baseClient.request(GetCapabilities)

    override suspend fun search(
        request: Search.Request,
        nextBatch: String?,
    ): Result<Search.Response> =
        baseClient.request(Search(nextBatch), request)

    override suspend fun whoIs(
        userId: UserId,
    ): Result<WhoIs.Response> =
        baseClient.request(WhoIs(userId))
}