package net.folivo.trixnity.clientserverapi.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*

class KeysApiClient(
    @PublishedApi
    internal val httpClient: MatrixClientServerApiHttpClient,
    @PublishedApi
    internal val json: Json
) {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
     */
    suspend fun setKeys(
        deviceKeys: SignedDeviceKeys? = null,
        oneTimeKeys: Keys? = null,
        asUserId: UserId? = null
    ): Result<Map<KeyAlgorithm, Int>> =
        httpClient.request(SetKeys(asUserId), SetKeys.Request(deviceKeys, oneTimeKeys))
            .mapCatching { it.oneTimeKeyCounts }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String? = null,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): Result<GetKeys.Response> =
        httpClient.request(GetKeys(asUserId), GetKeys.Request(deviceKeys, token, timeout))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): Result<ClaimKeys.Response> =
        httpClient.request(ClaimKeys(asUserId), ClaimKeys.Request(oneTimeKeys, timeout))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId? = null
    ): Result<GetKeyChanges.Response> =
        httpClient.request(GetKeyChanges(from, to, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysdevice_signingupload">matrix spec</a>
     */
    suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
        asUserId: UserId? = null
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(
            SetCrossSigningKeys(asUserId),
            SetCrossSigningKeys.Request(
                masterKey = masterKey,
                selfSigningKey = selfSigningKey,
                userSigningKey = userSigningKey
            )
        )

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
     */
    suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
        asUserId: UserId? = null
    ): Result<AddSignatures.Response> =
        httpClient.request(
            AddSignatures(asUserId),
            (signedDeviceKeys.associate {
                Pair(it.signed.userId, it.signed.deviceId) to json.encodeToJsonElement(it)
            } + signedCrossSigningKeys.associate {
                Pair(
                    it.signed.userId, it.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().first().value
                ) to json.encodeToJsonElement(it)
            }).entries.groupBy { it.key.first }
                .map { group -> group.key to group.value.associate { it.key.second to it.value } }.toMap()
        )

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend inline fun getRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<RoomsKeyBackup> =
        httpClient.request(GetRoomsKeyBackup(version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend inline fun getRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<RoomKeyBackup> =
        httpClient.request(GetRoomKeyBackup(roomId.e(), version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend inline fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<RoomKeyBackupData> =
        httpClient.request(GetRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend inline fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomsKeyBackup(version, asUserId), backup)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend inline fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomKeyBackup(roomId.e(), version, asUserId), backup)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend inline fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId), backup)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomsKeyBackup(version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomKeyBackup(roomId.e(), version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversion">matrix spec</a>
     */
    suspend fun getRoomKeysVersion(
        asUserId: UserId? = null
    ): Result<GetRoomKeysBackupVersionResponse> =
        httpClient.request(GetRoomKeyBackupVersion(asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun getRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<GetRoomKeysBackupVersionResponse> =
        httpClient.request(GetRoomKeyBackupVersionByVersion(version.e(), asUserId))

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3room_keysversion">matrix spec</a>
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
        asUserId: UserId? = null
    ): Result<String> {
        val version = request.version
        return if (version == null) {
            httpClient.request(SetRoomKeyBackupVersion(asUserId), request).map { it.version }
        } else {
            httpClient.request(SetRoomKeyBackupVersionByVersion(version.e(), asUserId), request).map { version }
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun deleteRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request(DeleteRoomKeyBackupVersion(version.e(), asUserId))
}