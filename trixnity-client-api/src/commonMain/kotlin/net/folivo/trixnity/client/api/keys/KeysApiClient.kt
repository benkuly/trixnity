package net.folivo.trixnity.client.api.keys

import io.ktor.client.*
import io.ktor.client.request.*
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.crypto.Signed

class KeysApiClient(val httpClient: HttpClient) {
    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-keys-upload">matrix spec</a>
     */
    suspend fun uploadKeys(
        deviceKeys: Signed<DeviceKeys, MatrixId.UserId>? = null,
        oneTimeKeys: Keys? = null,
        asUserId: MatrixId.UserId? = null
    ): Map<KeyAlgorithm, Int> {
        return httpClient.post<UploadKeysResponse> {
            url("/r0/keys/upload")
            parameter("user_id", asUserId)
            body = UploadKeysRequest(deviceKeys, oneTimeKeys)
        }.oneTimeKeyCounts
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-keys-query">matrix spec</a>
     */
    suspend fun getKeys(
        deviceKeys: Map<MatrixId.UserId, Set<String>>,
        token: String? = null,
        timeout: Int? = 10000,
        asUserId: MatrixId.UserId? = null
    ): QueryKeysResponse {
        return httpClient.post {
            url("/r0/keys/query")
            parameter("user_id", asUserId)
            body = QueryKeysRequest(deviceKeys, token, timeout)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-keys-claim">matrix spec</a>
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<MatrixId.UserId, Map<String, KeyAlgorithm>>,
        timeout: Int? = 10000,
        asUserId: MatrixId.UserId? = null
    ): ClaimKeysResponse {
        return httpClient.post {
            url("/r0/keys/claim")
            parameter("user_id", asUserId)
            body = ClaimKeysRequest(oneTimeKeys, timeout)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-keys-claim">matrix spec</a>
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: MatrixId.UserId? = null
    ): GetKeyChangesResponse {
        return httpClient.get {
            url("/r0/keys/changes")
            parameter("from", from)
            parameter("to", to)
            parameter("user_id", asUserId)
        }
    }
}