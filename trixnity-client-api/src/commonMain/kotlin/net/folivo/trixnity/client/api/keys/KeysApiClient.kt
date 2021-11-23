package net.folivo.trixnity.client.api.keys

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import net.folivo.trixnity.client.api.MatrixHttpClient
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.crypto.Signed

class KeysApiClient(
    val httpClient: MatrixHttpClient,
) {
    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
     */
    suspend fun uploadKeys(
        deviceKeys: Signed<DeviceKeys, UserId>? = null,
        oneTimeKeys: Keys? = null,
        asUserId: UserId? = null
    ): Map<KeyAlgorithm, Int> {
        return httpClient.request<UploadKeysResponse> {
            method = Post
            url("/_matrix/client/v3/keys/upload")
            parameter("user_id", asUserId)
            body = UploadKeysRequest(deviceKeys, oneTimeKeys)
        }.oneTimeKeyCounts
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String? = null,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): QueryKeysResponse {
        return httpClient.request {
            method = Post
            url("/_matrix/client/v3/keys/query")
            parameter("user_id", asUserId)
            body = QueryKeysRequest(deviceKeys, token, timeout)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): ClaimKeysResponse {
        return httpClient.request {
            method = Post
            url("/_matrix/client/v3/keys/claim")
            parameter("user_id", asUserId)
            body = ClaimKeysRequest(oneTimeKeys, timeout)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId? = null
    ): GetKeyChangesResponse {
        return httpClient.request {
            method = Get
            url("/_matrix/client/v3/keys/changes")
            parameter("from", from)
            parameter("to", to)
            parameter("user_id", asUserId)
        }
    }
}