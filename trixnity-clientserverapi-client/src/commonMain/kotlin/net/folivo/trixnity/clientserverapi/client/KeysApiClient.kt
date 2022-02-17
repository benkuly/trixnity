package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*

class KeysApiClient(
    val httpClient: MatrixHttpClient,
    val json: Json
) {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
     */
    suspend fun setDeviceKeys(
        deviceKeys: SignedDeviceKeys? = null,
        oneTimeKeys: Keys? = null,
        asUserId: UserId? = null
    ): Result<Map<KeyAlgorithm, Int>> =
        httpClient.request<SetDeviceKeysResponse> {
            method = Post
            url("/_matrix/client/v3/keys/upload")
            parameter("user_id", asUserId)
            setBody(SetDeviceKeysRequest(deviceKeys, oneTimeKeys))
        }.mapCatching { it.oneTimeKeyCounts }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
     */
    suspend fun getKeys(
        deviceKeys: Map<UserId, Set<String>>,
        token: String? = null,
        timeout: Int? = 10000,
        asUserId: UserId? = null
    ): Result<QueryKeysResponse> =
        httpClient.request<String> {
            method = Post
            url("/_matrix/client/v3/keys/query")
            parameter("user_id", asUserId)
            setBody(QueryKeysRequest(deviceKeys, token, timeout))
        }.mapCatching { json.decodeFromString(CatchingQueryKeysResponseSerializer, it) }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
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
            setBody(ClaimKeysRequest(oneTimeKeys, timeout))
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
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
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysdevice_signingupload">matrix spec</a>
     */
    suspend fun setCrossSigningKeys(
        masterKey: SignedCrossSigningKeys?,
        selfSigningKey: SignedCrossSigningKeys?,
        userSigningKey: SignedCrossSigningKeys?,
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
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
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
            setBody(
                (signedDeviceKeys.associate {
                    Pair(it.signed.userId, it.signed.deviceId) to json.encodeToJsonElement(it)
                } + signedCrossSigningKeys.associate {
                    Pair(
                        it.signed.userId, it.signed.keys.keys.filterIsInstance<Key.Ed25519Key>().first().value
                    ) to json.encodeToJsonElement(it)
                }).entries.groupBy { it.key.first }
                    .map { group -> group.key to group.value.associate { it.key.second to it.value } }.toMap()
            )
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun <T : RoomKeyBackupSessionData> getRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        asUserId: UserId? = null
    ): Result<RoomsKeyBackup<T>> =
        httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/room_keys/keys")
            parameter("version", version)
            parameter("user_id", asUserId)
        }.mapCatching { json.decodeFromString(RoomsKeyBackup.serializer(sessionDataSerializer), it) }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> getRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<RoomsKeyBackup<T>> = getRoomKeys(serializer(), version, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun <T : RoomKeyBackupSessionData> getRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<RoomKeyBackup<T>> = httpClient.request<String> {
        method = Get
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
    }.mapCatching { json.decodeFromString(RoomKeyBackup.serializer(sessionDataSerializer), it) }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> getRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<RoomKeyBackup<T>> = getRoomKeys(serializer(), version, roomId, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun <T : RoomKeyBackupSessionData> getRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<RoomKeyBackupData<T>> = httpClient.request<String> {
        method = Get
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}/${sessionId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
    }.mapCatching { json.decodeFromString(RoomKeyBackupData.serializer(sessionDataSerializer), it) }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> getRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<RoomKeyBackupData<T>> = getRoomKeys(serializer(), version, roomId, sessionId, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun <T : RoomKeyBackupSessionData> setRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        backup: RoomsKeyBackup<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = httpClient.request {
        method = Put
        url("/_matrix/client/v3/room_keys/keys")
        parameter("version", version)
        parameter("user_id", asUserId)
        setBody(json.encodeToJsonElement(RoomsKeyBackup.serializer(sessionDataSerializer), backup))
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> setRoomKeys(
        version: String,
        backup: RoomsKeyBackup<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = setRoomKeys(serializer(), version, backup, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun <T : RoomKeyBackupSessionData> setRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = httpClient.request {
        method = Put
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
        setBody(json.encodeToJsonElement(RoomKeyBackup.serializer(sessionDataSerializer), backup))
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> setRoomKeys(
        version: String,
        roomId: RoomId,
        backup: RoomKeyBackup<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = setRoomKeys(serializer(), version, roomId, backup, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> setRoomKeys(
        sessionDataSerializer: KSerializer<T>,
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = httpClient.request {
        method = Put
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}/${sessionId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
        setBody(json.encodeToJsonElement(RoomKeyBackupData.serializer(sessionDataSerializer), backup))
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend inline fun <reified T : RoomKeyBackupSessionData> setRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        backup: RoomKeyBackupData<T>,
        asUserId: UserId? = null
    ): Result<SetRoomKeysResponse> = setRoomKeys(serializer(), version, roomId, sessionId, backup, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> = httpClient.request {
        method = Delete
        url("/_matrix/client/v3/room_keys/keys")
        parameter("version", version)
        parameter("user_id", asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> = httpClient.request {
        method = Delete
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun deleteRoomKeys(
        version: String,
        roomId: RoomId,
        sessionId: String,
        asUserId: UserId? = null
    ): Result<DeleteRoomKeysResponse> = httpClient.request {
        method = Delete
        url("/_matrix/client/v3/room_keys/keys/${roomId.e()}/${sessionId.e()}")
        parameter("version", version)
        parameter("user_id", asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversion">matrix spec</a>
     */
    suspend fun getRoomKeysVersion(
        asUserId: UserId? = null
    ): Result<GetRoomKeysVersionResponse> = httpClient.request {
        method = Get
        url("/_matrix/client/v3/room_keys/version")
        parameter("user_id", asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun getRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<GetRoomKeysVersionResponse> = httpClient.request {
        method = Get
        url("/_matrix/client/v3/room_keys/version/$version")
        parameter("user_id", asUserId)
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3room_keysversion">matrix spec</a>
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun setRoomKeysVersion(
        request: SetRoomKeysVersionRequest,
        asUserId: UserId? = null
    ): Result<String> {
        val version = request.version
        return if (version == null) {
            httpClient.request<AddRoomKeysVersionResponse> {
                method = Post
                url("/_matrix/client/v3/room_keys/version")
                parameter("user_id", asUserId)
                setBody(request)
            }.map { it.version }
        } else {
            httpClient.request<Unit> {
                method = Put
                url("/_matrix/client/v3/room_keys/version/$version")
                parameter("user_id", asUserId)
                setBody(request)
            }.map { version }
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun deleteRoomKeysVersion(
        version: String,
        asUserId: UserId? = null
    ): Result<Unit> = httpClient.request {
        method = Delete
        url("/_matrix/client/v3/room_keys/version/$version")
        parameter("user_id", asUserId)
    }
}