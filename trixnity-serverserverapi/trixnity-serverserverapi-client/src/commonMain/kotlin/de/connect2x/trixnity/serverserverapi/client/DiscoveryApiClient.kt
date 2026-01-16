package de.connect2x.trixnity.serverserverapi.client

import io.ktor.http.*
import de.connect2x.trixnity.api.client.MatrixApiClient
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.serverserverapi.model.discovery.*

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
    private val baseClient: MatrixApiClient
) : DiscoveryApiClient {
    override suspend fun getWellKnown(baseUrl: Url): Result<GetWellKnown.Response> =
        baseClient.request(GetWellKnown) { mergeUrl(baseUrl) }


    override suspend fun getServerVersion(baseUrl: Url): Result<GetServerVersion.Response> =
        baseClient.request(GetServerVersion) { mergeUrl(baseUrl) }

    override suspend fun getServerKeys(baseUrl: Url): Result<Signed<ServerKeys, String>> =
        baseClient.request(GetServerKeys) { mergeUrl(baseUrl) }

    override suspend fun queryServerKeys(
        baseUrl: Url,
        request: QueryServerKeys.Request
    ): Result<QueryServerKeysResponse> =
        baseClient.request(QueryServerKeys, request) { mergeUrl(baseUrl) }

    override suspend fun queryKeysByServer(
        baseUrl: Url,
        serverName: String,
        minimumValidUntil: Long?
    ): Result<QueryServerKeysResponse> =
        baseClient.request(QueryServerKeysByServer(serverName, minimumValidUntil)) { mergeUrl(baseUrl) }
}