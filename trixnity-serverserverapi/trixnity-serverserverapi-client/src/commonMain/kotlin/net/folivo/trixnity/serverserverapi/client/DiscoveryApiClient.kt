package net.folivo.trixnity.serverserverapi.client

import io.ktor.http.*
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.serverserverapi.model.discovery.*

interface DiscoveryApiClient {
    /**
     * @see [GetWellKnown]
     */
    suspend fun getWellKnown(baseUrl: Url): Result<GetWellKnown.Response>

    /**
     * @see [GetServerVersion]
     */
    suspend fun getServerVersion(baseUrl: Url): Result<GetServerVersion.Response>

    /**
     * @see [GetServerKeys]
     */
    suspend fun getServerKeys(baseUrl: Url): Result<Signed<ServerKeys, String>>

    /**
     * @see [QueryServerKeys]
     */
    suspend fun queryServerKeys(baseUrl: Url, request: QueryServerKeys.Request): Result<QueryServerKeysResponse>

    /**
     * @see [QueryServerKeysByServer]
     */
    suspend fun queryKeysByServer(
        baseUrl: Url,
        serverName: String,
        minimumValidUntil: Long? = null
    ): Result<QueryServerKeysResponse>
}

class DiscoveryApiClientImpl(
    private val httpClient: MatrixApiClient
) : DiscoveryApiClient {
    override suspend fun getWellKnown(baseUrl: Url): Result<GetWellKnown.Response> =
        httpClient.request(GetWellKnown) { mergeUrl(baseUrl) }


    override suspend fun getServerVersion(baseUrl: Url): Result<GetServerVersion.Response> =
        httpClient.request(GetServerVersion) { mergeUrl(baseUrl) }

    override suspend fun getServerKeys(baseUrl: Url): Result<Signed<ServerKeys, String>> =
        httpClient.request(GetServerKeys()) { mergeUrl(baseUrl) }

    override suspend fun queryServerKeys(
        baseUrl: Url,
        request: QueryServerKeys.Request
    ): Result<QueryServerKeysResponse> =
        httpClient.request(QueryServerKeys, request) { mergeUrl(baseUrl) }

    override suspend fun queryKeysByServer(
        baseUrl: Url,
        serverName: String,
        minimumValidUntil: Long?
    ): Result<QueryServerKeysResponse> =
        httpClient.request(QueryServerKeysByServer(serverName, minimumValidUntil)) { mergeUrl(baseUrl) }
}