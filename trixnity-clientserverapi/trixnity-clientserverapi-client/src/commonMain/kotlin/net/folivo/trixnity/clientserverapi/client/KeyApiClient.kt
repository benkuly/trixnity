package net.folivo.trixnity.clientserverapi.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.clientserverapi.model.key.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*

interface KeyApiClient {
    /**
     * @see [SetKeys]
     */
    suspend fun setKeys(
        deviceKeys: SignedDeviceKeys? = null,
        oneTimeKeys: Keys? = null,
        fallbackKeys: Keys? = null,
    ): Result<Map<KeyAlgorithm, Int>>

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        timeout: Long? = 10000,
    ): Result<GetKeys.Response>

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Long? = 10000,
    ): Result<ClaimKeys.Response>

    /**
     * @see [GetKeyChanges]
     */
    suspend fun getKeyChanges(
        from: String,
        to: String,
    ): Result<GetKeyChanges.Response>

    /**
     * @see [SetCrossSigningKeys]
     */
    suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
    ): Result<UIA<Unit>>

    /**
     * @see [AddSignatures]
     */
    suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
    ): Result<AddSignatures.Response>

    /**
     * @see [GetRoomsKeyBackup]
     */
    suspend fun getRoomKeys(
        version: String,
    ): Result<RoomsKeyBackup>

    /**
     * @see [GetRoomKeyBackup]
     */
    suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
    ): Result<RoomKeyBackup>

    /**
     * @see [GetRoomKeyBackupData]
     */
    suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
    ): Result<RoomKeyBackupData>

    /**
     * @see [SetRoomsKeyBackup]
     */
    suspend fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
    ): Result<SetRoomKeysResponse>

    /**
     * @see [SetRoomKeyBackup]
     */
    suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
    ): Result<SetRoomKeysResponse>

    /**
     * @see [SetRoomKeyBackupData]
     */
    suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
    ): Result<SetRoomKeysResponse>

    /**
     * @see [DeleteRoomsKeyBackup]
     */
    suspend fun deleteRoomKeys(
        version: String,
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [DeleteRoomKeyBackup]
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [DeleteRoomKeyBackupData]
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
    ): Result<DeleteRoomKeysResponse>

    /**
     * @see [GetRoomKeyBackupVersion]
     */
    suspend fun getRoomKeysVersion(
    ): Result<GetRoomKeysBackupVersionResponse>

    /**
     * @see [GetRoomKeyBackupVersionByVersion]
     */
    suspend fun getRoomKeysVersion(
        version: String,
    ): Result<GetRoomKeysBackupVersionResponse>

    /**
     * @see [SetRoomKeyBackupVersion]
     * @see [SetRoomKeyBackupVersionByVersion]
     */
    suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
    ): Result<String>

    /**
     * @see [DeleteRoomKeyBackupVersion]
     */
    suspend fun deleteRoomKeysVersion(
        version: String,
    ): Result<Unit>
}

class KeyApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    private val json: Json
) : KeyApiClient {
    override suspend fun setKeys(
        deviceKeys: SignedDeviceKeys?,
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
    ): Result<Map<KeyAlgorithm, Int>> =
        baseClient.request(SetKeys, SetKeys.Request(deviceKeys, oneTimeKeys, fallbackKeys))
            .mapCatching { it.oneTimeKeyCounts }

    override suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        timeout: Long?,
    ): Result<GetKeys.Response> =
        baseClient.request(GetKeys, GetKeys.Request(deviceKeys, timeout))

    override suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Long?,
    ): Result<ClaimKeys.Response> =
        baseClient.request(ClaimKeys, ClaimKeys.Request(oneTimeKeys, timeout))

    override suspend fun getKeyChanges(
        from: String,
        to: String,
    ): Result<GetKeyChanges.Response> =
        baseClient.request(GetKeyChanges(from, to))

    override suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(
            SetCrossSigningKeys,
            SetCrossSigningKeys.Request(
                masterKey = masterKey,
                selfSigningKey = selfSigningKey,
                userSigningKey = userSigningKey
            )
        )

    override suspend fun addSignatures(
        signedDeviceKeys: Set<SignedDeviceKeys>,
        signedCrossSigningKeys: Set<SignedCrossSigningKeys>,
    ): Result<AddSignatures.Response> =
        baseClient.request(
            AddSignatures,
            (signedDeviceKeys.associate {
                Pair(it.signed.userId, it.signed.deviceId) to json.encodeToJsonElement(it)
            } + signedCrossSigningKeys.associate {
                Pair(
                    it.signed.userId, it.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().first().value.value
                ) to json.encodeToJsonElement(it)
            }).entries.groupBy { it.key.first }
                .map { group -> group.key to group.value.associate { it.key.second to it.value } }.toMap()
        )

    override suspend fun getRoomKeys(
        version: String,
    ): Result<RoomsKeyBackup> =
        baseClient.request(GetRoomsKeyBackup(version))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
    ): Result<RoomKeyBackup> =
        baseClient.request(GetRoomKeyBackup(roomId, version))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
    ): Result<RoomKeyBackupData> =
        baseClient.request(GetRoomKeyBackupData(roomId, sessionId, version))

    override suspend fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomsKeyBackup(version), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomKeyBackup(roomId, version), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomKeyBackupData(roomId, sessionId, version), backup)

    override suspend fun deleteRoomKeys(
        version: String,
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomsKeyBackup(version))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomKeyBackup(roomId, version))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomKeyBackupData(roomId, sessionId, version))

    override suspend fun getRoomKeysVersion(): Result<GetRoomKeysBackupVersionResponse> =
        baseClient.request(GetRoomKeyBackupVersion)

    override suspend fun getRoomKeysVersion(
        version: String,
    ): Result<GetRoomKeysBackupVersionResponse> =
        baseClient.request(GetRoomKeyBackupVersionByVersion(version))

    override suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
    ): Result<String> {
        val version = request.version
        return if (version == null) {
            baseClient.request(SetRoomKeyBackupVersion, request).map { it.version }
        } else {
            baseClient.request(SetRoomKeyBackupVersionByVersion(version), request).map { version }
        }
    }

    override suspend fun deleteRoomKeysVersion(
        version: String,
    ): Result<Unit> =
        baseClient.request(DeleteRoomKeyBackupVersion(version))
}