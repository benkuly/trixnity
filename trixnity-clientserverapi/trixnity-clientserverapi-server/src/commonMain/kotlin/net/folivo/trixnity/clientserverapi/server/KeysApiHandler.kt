package net.folivo.trixnity.clientserverapi.server

import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackup
import net.folivo.trixnity.core.model.keys.RoomKeyBackupData
import net.folivo.trixnity.core.model.keys.RoomsKeyBackup

interface KeysApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
     */
    suspend fun setKeys(context: MatrixEndpointContext<SetKeys, SetKeys.Request, SetKeys.Response>): SetKeys.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
     */
    suspend fun getKeys(context: MatrixEndpointContext<GetKeys, GetKeys.Request, GetKeys.Response>): GetKeys.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
     */
    suspend fun claimKeys(context: MatrixEndpointContext<ClaimKeys, ClaimKeys.Request, ClaimKeys.Response>): ClaimKeys.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
     */
    suspend fun getKeyChanges(context: MatrixEndpointContext<GetKeyChanges, Unit, GetKeyChanges.Response>): GetKeyChanges.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysdevice_signingupload">matrix spec</a>
     */
    suspend fun setCrossSigningKeys(context: MatrixEndpointContext<SetCrossSigningKeys, RequestWithUIA<SetCrossSigningKeys.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
     */
    suspend fun addSignatures(context: MatrixEndpointContext<AddSignatures, Map<UserId, Map<String, JsonElement>>, AddSignatures.Response>): AddSignatures.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun getRoomsKeyBackup(context: MatrixEndpointContext<GetRoomsKeyBackup, Unit, RoomsKeyBackup>): RoomsKeyBackup

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun getRoomKeyBackup(context: MatrixEndpointContext<GetRoomKeyBackup, Unit, RoomKeyBackup>): RoomKeyBackup

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun getRoomKeyBackupData(context: MatrixEndpointContext<GetRoomKeyBackupData, Unit, RoomKeyBackupData>): RoomKeyBackupData

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun setRoomsKeyBackup(context: MatrixEndpointContext<SetRoomsKeyBackup, RoomsKeyBackup, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun setRoomKeyBackup(context: MatrixEndpointContext<SetRoomKeyBackup, RoomKeyBackup, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun setRoomKeyBackupData(context: MatrixEndpointContext<SetRoomKeyBackupData, RoomKeyBackupData, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeys">matrix spec</a>
     */
    suspend fun deleteRoomsKeyBackup(context: MatrixEndpointContext<DeleteRoomsKeyBackup, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomid">matrix spec</a>
     */
    suspend fun deleteRoomKeyBackup(context: MatrixEndpointContext<DeleteRoomKeyBackup, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keyskeysroomidsessionid">matrix spec</a>
     */
    suspend fun deleteRoomKeyBackupData(context: MatrixEndpointContext<DeleteRoomKeyBackupData, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversion">matrix spec</a>
     */
    suspend fun getRoomKeyBackupVersion(context: MatrixEndpointContext<GetRoomKeyBackupVersion, Unit, GetRoomKeysBackupVersionResponse>): GetRoomKeysBackupVersionResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun getRoomKeyBackupVersionByVersion(context: MatrixEndpointContext<GetRoomKeyBackupVersionByVersion, Unit, GetRoomKeysBackupVersionResponse>): GetRoomKeysBackupVersionResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3room_keysversion">matrix spec</a>
     */
    suspend fun setRoomKeyBackupVersion(context: MatrixEndpointContext<SetRoomKeyBackupVersion, SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response>): SetRoomKeyBackupVersion.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun setRoomKeyBackupVersionByVersion(context: MatrixEndpointContext<SetRoomKeyBackupVersionByVersion, SetRoomKeyBackupVersionRequest, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3room_keysversionversion">matrix spec</a>
     */
    suspend fun deleteRoomKeyBackupVersion(context: MatrixEndpointContext<DeleteRoomKeyBackupVersion, Unit, Unit>)
}