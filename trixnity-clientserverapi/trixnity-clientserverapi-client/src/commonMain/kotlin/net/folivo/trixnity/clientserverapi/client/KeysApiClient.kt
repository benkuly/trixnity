package net.folivo.trixnity.clientserverapi.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*

interface KeysApiClient {
    /**
     * @see [SetKeys]
     */
    suspend fun setKeys(
        deviceKeys: SignedDeviceKeys? = null,
        oneTimeKeys: Keys? = null,
        asUserId: UserId? = null
    ): Result<Map<KeyAlgorithm, Int>>

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String? = null,
        timeout: Long? = 10000,
        asUserId: UserId? = null
    ): Result<GetKeys.Response>

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Long? = 10000,
        asUserId: UserId? = null
    ): Result<ClaimKeys.Response>

    /**
     * @see [GetKeyChanges]
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId? = null
    ): Result<GetKeyChanges.Response>

    /**
     * @see [SetCrossSigningKeys]
     */
    suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
        asUserId: UserId? = null
    ): Result<UIA<Unit>>

    /**
     * @see [AddSignatures]
     */
    suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
        asUserId: UserId? = null
    ): Result<AddSignatures.Response>

    /**
     * @see [GetRoomsKeyBackup]
     */
    suspend fun getRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<RoomsKeyBackup>

    /**
     * @see [GetRoomKeyBackup]
     */
    suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<RoomKeyBackup>

    /**
     * @see [GetRoomKeyBackupData]
     */
    suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<RoomKeyBackupData>

    /**
     * @see [SetRoomsKeyBackup]
     */
    suspend fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse>

    /**
     * @see [SetRoomKeyBackup]
     */
    suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse>

    /**
     * @see [SetRoomKeyBackupData]
     */
    suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse>

    /**
     * @see [DeleteRoomsKeyBackup]
     */
    suspend fun deleteRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [DeleteRoomKeyBackup]
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [DeleteRoomKeyBackupData]
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [GetRoomKeyBackupVersion]
     */
    suspend fun getRoomKeysVersion(
        asUserId: UserId? = null
    ): Result<GetRoomKeysBackupVersionResponse>

    /**
     * @see [GetRoomKeyBackupVersionByVersion]
     */
    suspend fun getRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<GetRoomKeysBackupVersionResponse>

    /**
     * @see [SetRoomKeyBackupVersion]
     * @see [SetRoomKeyBackupVersionByVersion]
     */
    suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
        asUserId: UserId? = null
    ): Result<String>

    /**
     * @see [DeleteRoomKeyBackupVersion]
     */
    suspend fun deleteRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<Unit>
}

class KeysApiClientImpl(
    private val httpClient: MatrixClientServerApiHttpClient,
    private val json: Json
) : KeysApiClient {
    override suspend fun setKeys(
        deviceKeys: SignedDeviceKeys?,
        oneTimeKeys: Keys?,
        asUserId: UserId?
    ): Result<Map<KeyAlgorithm, Int>> =
        httpClient.request(SetKeys(asUserId), SetKeys.Request(deviceKeys, oneTimeKeys))
            .mapCatching { it.oneTimeKeyCounts }

    override suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String?,
        timeout: Long?,
        asUserId: UserId?
    ): Result<GetKeys.Response> =
        httpClient.request(GetKeys(asUserId), GetKeys.Request(deviceKeys, token, timeout))

    override suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Long?,
        asUserId: UserId?
    ): Result<ClaimKeys.Response> =
        httpClient.request(ClaimKeys(asUserId), ClaimKeys.Request(oneTimeKeys, timeout))

    override suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId?
    ): Result<GetKeyChanges.Response> =
        httpClient.request(GetKeyChanges(from, to, asUserId))

    override suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
        asUserId: UserId?
    ): Result<UIA<Unit>> =
        httpClient.uiaRequest(
            SetCrossSigningKeys(asUserId),
            SetCrossSigningKeys.Request(
                masterKey = masterKey,
                selfSigningKey = selfSigningKey,
                userSigningKey = userSigningKey
            )
        )

    override suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
        asUserId: UserId?
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

    override suspend fun getRoomKeys(
        version: String,
        asUserId: UserId?
    ): Result<RoomsKeyBackup> =
        httpClient.request(GetRoomsKeyBackup(version, asUserId))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId?
    ): Result<RoomKeyBackup> =
        httpClient.request(GetRoomKeyBackup(roomId.e(), version, asUserId))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId?
    ): Result<RoomKeyBackupData> =
        httpClient.request(GetRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId))

    override suspend fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomsKeyBackup(version, asUserId), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomKeyBackup(roomId.e(), version, asUserId), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        httpClient.request(SetRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId), backup)

    override suspend fun deleteRoomKeys(
        version: String,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomsKeyBackup(version, asUserId))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomKeyBackup(roomId.e(), version, asUserId))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        httpClient.request(DeleteRoomKeyBackupData(roomId.e(), sessionId.e(), version, asUserId))

    override suspend fun getRoomKeysVersion(
        asUserId: UserId?
    ): Result<GetRoomKeysBackupVersionResponse> =
        httpClient.request(GetRoomKeyBackupVersion(asUserId))

    override suspend fun getRoomKeysVersion(
        version: String,
        asUserId: UserId?
    ): Result<GetRoomKeysBackupVersionResponse> =
        httpClient.request(GetRoomKeyBackupVersionByVersion(version.e(), asUserId))

    override suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
        asUserId: UserId?
    ): Result<String> {
        val version = request.version
        return if (version == null) {
            httpClient.request(SetRoomKeyBackupVersion(asUserId), request).map { it.version }
        } else {
            httpClient.request(SetRoomKeyBackupVersionByVersion(version.e(), asUserId), request).map { version }
        }
    }

    override suspend fun deleteRoomKeysVersion(
        version: String,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(DeleteRoomKeyBackupVersion(version.e(), asUserId))
}