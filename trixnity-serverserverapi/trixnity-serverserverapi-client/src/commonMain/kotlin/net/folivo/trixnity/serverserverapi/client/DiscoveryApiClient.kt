package net.folivo.trixnity.serverserverapi.client

import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.discovery.*

interface IDiscoveryApiClient {
    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(): Result<GetWellKnown.Response>

    /**
     * @see [GetServerVersion]
     */
    suspend fun getServerVersion(): Result<GetServerVersion.Response>

    /**
     * @see [GetServerKeys]
     */
    suspend fun getServerKeys(): Result<Signed<ServerKeys, String>>

    /**
     * @see [QueryServerKeys]
     */
    suspend fun queryServerKeys(request: QueryServerKeys.Request): Result<QueryServerKeysResponse>

    /**
     * @see [QueryServerKeysByServer]
     */
    suspend fun queryKeysByServer(
        serverName: String,
        minimumValidUntil: Long? = null
    ): Result<QueryServerKeysResponse>
}

class DiscoveryApiClient(
    private val httpClient: MatrixApiClient
) : IDiscoveryApiClient {
    override suspend fun getWellKnown(): Result<GetWellKnown.Response> =
        httpClient.request(GetWellKnown)

    override suspend fun getServerVersion(): Result<GetServerVersion.Response> =
        httpClient.request(GetServerVersion)

    override suspend fun getServerKeys(): Result<Signed<ServerKeys, String>> =
        httpClient.request(GetServerKeys)

    override suspend fun queryServerKeys(request: QueryServerKeys.Request): Result<QueryServerKeysResponse> =
        httpClient.request(QueryServerKeys, request)

    override suspend fun queryKeysByServer(
        serverName: String,
        minimumValidUntil: Long?
    ): Result<QueryServerKeysResponse> =
        httpClient.request(QueryServerKeysByServer(serverName, minimumValidUntil))
}