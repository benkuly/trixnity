package net.folivo.trixnity.clientserverapi.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.clientserverapi.model.keys.*
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
        asUserId: UserId? = null
    ): Result<Map<KeyAlgorithm, Int>>

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
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

class KeyApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    private val json: Json
) : KeyApiClient {
    override suspend fun setKeys(
        deviceKeys: SignedDeviceKeys?,
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
        asUserId: UserId?
    ): Result<Map<KeyAlgorithm, Int>> =
        baseClient.request(SetKeys(asUserId), SetKeys.Request(deviceKeys, oneTimeKeys, fallbackKeys))
            .mapCatching { it.oneTimeKeyCounts }

    override suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        timeout: Long?,
        asUserId: UserId?
    ): Result<GetKeys.Response> =
        baseClient.request(GetKeys(asUserId), GetKeys.Request(deviceKeys, timeout))

    override suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        timeout: Long?,
        asUserId: UserId?
    ): Result<ClaimKeys.Response> =
        baseClient.request(ClaimKeys(asUserId), ClaimKeys.Request(oneTimeKeys, timeout))

    override suspend fun getKeyChanges(
        from: String,
        to: String,
        asUserId: UserId?
    ): Result<GetKeyChanges.Response> =
        baseClient.request(GetKeyChanges(from, to, asUserId))

    override suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
        asUserId: UserId?
    ): Result<UIA<Unit>> =
        baseClient.uiaRequest(
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
        baseClient.request(
            AddSignatures(asUserId),
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
        asUserId: UserId?
    ): Result<RoomsKeyBackup> =
        baseClient.request(GetRoomsKeyBackup(version, asUserId))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId?
    ): Result<RoomKeyBackup> =
        baseClient.request(GetRoomKeyBackup(roomId, version, asUserId))

    override suspend fun getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId?
    ): Result<RoomKeyBackupData> =
        baseClient.request(GetRoomKeyBackupData(roomId, sessionId, version, asUserId))

    override suspend fun setRoomKeys(
        version: String,
        backup: RoomsKeyBackup,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomsKeyBackup(version, asUserId), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomKeyBackup(roomId, version, asUserId), backup)

    override suspend fun setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData,
        asUserId: UserId?
    ): Result<SetRoomKeysResponse> =
        baseClient.request(SetRoomKeyBackupData(roomId, sessionId, version, asUserId), backup)

    override suspend fun deleteRoomKeys(
        version: String,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomsKeyBackup(version, asUserId))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomKeyBackup(roomId, version, asUserId))

    override suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId?
    ): Result<DeleteRoomKeysResponse> =
        baseClient.request(DeleteRoomKeyBackupData(roomId, sessionId, version, asUserId))

    override suspend fun getRoomKeysVersion(
        asUserId: UserId?
    ): Result<GetRoomKeysBackupVersionResponse> =
        baseClient.request(GetRoomKeyBackupVersion(asUserId))

    override suspend fun getRoomKeysVersion(
        version: String,
        asUserId: UserId?
    ): Result<GetRoomKeysBackupVersionResponse> =
        baseClient.request(GetRoomKeyBackupVersionByVersion(version, asUserId))

    override suspend fun setRoomKeysVersion(
        request: SetRoomKeyBackupVersionRequest,
        asUserId: UserId?
    ): Result<String> {
        val version = request.version
        return if (version == null) {
            baseClient.request(SetRoomKeyBackupVersion(asUserId), request).map { it.version }
        } else {
            baseClient.request(SetRoomKeyBackupVersionByVersion(version, asUserId), request).map { version }
        }
    }

    override suspend fun deleteRoomKeysVersion(
        version: String,
        asUserId: UserId?
    ): Result<Unit> =
        baseClient.request(DeleteRoomKeyBackupVersion(version, asUserId))
}