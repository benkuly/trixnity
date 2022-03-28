package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.core.model.UserId

class ServerApiClient(private val httpClient: MatrixClientServerApiHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientversions">matrix spec</a>
     */
    suspend fun getVersions(): Result<GetVersions.Response> =
        httpClient.request(GetVersions)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
     */
    suspend fun getCapabilities(): Result<GetCapabilities.Response> =
        httpClient.request(GetCapabilities)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3search">matrix spec</a>
     */
    suspend fun search(
        request: Search.Request,
        nextBatch: String? = null,
        asUserId: UserId? = null
    ): Result<Search.Response> =
        httpClient.request(Search(nextBatch, asUserId), request)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3adminwhoisuserid">matrix spec</a>
     */
    suspend fun whoIs(
        userId: UserId,
        asUserId: UserId? = null
    ): Result<WhoIs.Response> =
        httpClient.request(WhoIs(userId, asUserId))
}