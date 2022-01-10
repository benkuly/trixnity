package net.folivo.trixnity.client.api

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.api.model.keys.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*

class KeysApiClient(
    val httpClient: MatrixHttpClient,
    val json: Json
) {
    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
     */
    suspend fun setDeviceKeys(
        deviceKeys: Signed<DeviceKeys, UserId>? = null,
        oneTimeKeys: Keys? = null,
        asUserId: UserId? = null
    ): Result<Map<KeyAlgorithm, Int>> =
        httpClient.request<SetDeviceKeysResponse> {
            method = Post
            url("/_matrix/client/v3/keys/upload")
            parameter("user_id", asUserId)
            body = SetDeviceKeysRequest(deviceKeys, oneTimeKeys)
        }.mapCatching { it.oneTimeKeyCounts }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String? = null,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): Result<QueryKeysResponse> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/keys/query")
            parameter("user_id", asUserId)
            body = QueryKeysRequest(deviceKeys, token, timeout)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): Result<ClaimKeysResponse> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/keys/claim")
            parameter("user_id", asUserId)
            body = ClaimKeysRequest(oneTimeKeys, timeout)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId? = null
    ): Result<GetKeyChangesResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/keys/changes")
            parameter("from", from)
            parameter("to", to)
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keysdevice_signingupload">matrix spec</a>
     */
    suspend fun setCrossSigningKeys(
        masterKey: Signed<CrossSigningKeys, UserId>?,
        selfSigningKey: Signed<CrossSigningKeys, UserId>?,
        userSigningKey: Signed<CrossSigningKeys, UserId>?,
        asUserId: UserId? = null
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(
            body = SetCrossSigningKeysRequest(
                masterKey = masterKey,
                selfSigningKey = selfSigningKey,
                userSigningKey = userSigningKey
            )
        ) {
            method = Post
            url("/_matrix/client/v3/keys/device_signing/upload")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
     */
    suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
        asUserId: UserId? = null
    ): Result<AddSignaturesResponse> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/keys/signatures/upload")
            parameter("user_id", asUserId)
            body = (signedDeviceKeys.associate {
                Pair(it.signed.userId, it.signed.deviceId) to json.encodeToJsonElement(it)
            } + signedCrossSigningKeys.associate {
                Pair(
                    it.signed.userId, it.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().first().value
                ) to json.encodeToJsonElement(it)
            }).entries.groupBy { it.key.first }
                .map { group -> group.key to group.value.associate { it.key.second to it.value } }.toMap()
        }
}