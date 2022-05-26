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
     * @see [SetKeys]
     */
    suspend fun setKeys(context: MatrixEndpointContext<SetKeys, SetKeys.Request, SetKeys.Response>): SetKeys.Response

    /**
     * @see [GetKeys]
     */
    suspend fun getKeys(context: MatrixEndpointContext<GetKeys, GetKeys.Request, GetKeys.Response>): GetKeys.Response

    /**
     * @see [ClaimKeys]
     */
    suspend fun claimKeys(context: MatrixEndpointContext<ClaimKeys, ClaimKeys.Request, ClaimKeys.Response>): ClaimKeys.Response

    /**
     * @see [GetKeyChanges]
     */
    suspend fun getKeyChanges(context: MatrixEndpointContext<GetKeyChanges, Unit, GetKeyChanges.Response>): GetKeyChanges.Response

    /**
     * @see [SetCrossSigningKeys]
     */
    suspend fun setCrossSigningKeys(context: MatrixEndpointContext<SetCrossSigningKeys, RequestWithUIA<SetCrossSigningKeys.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see [AddSignatures]
     */
    suspend fun addSignatures(context: MatrixEndpointContext<AddSignatures, Map<UserId, Map<String, JsonElement>>, AddSignatures.Response>): AddSignatures.Response

    /**
     * @see [GetRoomsKeyBackup]
     */
    suspend fun getRoomsKeyBackup(context: MatrixEndpointContext<GetRoomsKeyBackup, Unit, RoomsKeyBackup>): RoomsKeyBackup

    /**
     * @see [GetRoomKeyBackup]
     */
    suspend fun getRoomKeyBackup(context: MatrixEndpointContext<GetRoomKeyBackup, Unit, RoomKeyBackup>): RoomKeyBackup

    /**
     * @see [GetRoomKeyBackupData]
     */
    suspend fun getRoomKeyBackupData(context: MatrixEndpointContext<GetRoomKeyBackupData, Unit, RoomKeyBackupData>): RoomKeyBackupData

    /**
     * @see [SetRoomsKeyBackup]
     */
    suspend fun setRoomsKeyBackup(context: MatrixEndpointContext<SetRoomsKeyBackup, RoomsKeyBackup, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see [SetRoomKeyBackup]
     */
    suspend fun setRoomKeyBackup(context: MatrixEndpointContext<SetRoomKeyBackup, RoomKeyBackup, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see [SetRoomKeyBackupData]
     */
    suspend fun setRoomKeyBackupData(context: MatrixEndpointContext<SetRoomKeyBackupData, RoomKeyBackupData, SetRoomKeysResponse>): SetRoomKeysResponse

    /**
     * @see [DeleteRoomsKeyBackup]
     */
    suspend fun deleteRoomsKeyBackup(context: MatrixEndpointContext<DeleteRoomsKeyBackup, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see [DeleteRoomKeyBackup]
     */
    suspend fun deleteRoomKeyBackup(context: MatrixEndpointContext<DeleteRoomKeyBackup, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see [DeleteRoomKeyBackupData]
     */
    suspend fun deleteRoomKeyBackupData(context: MatrixEndpointContext<DeleteRoomKeyBackupData, Unit, DeleteRoomKeysResponse>): DeleteRoomKeysResponse

    /**
     * @see [GetRoomKeyBackupVersion]
     */
    suspend fun getRoomKeyBackupVersion(context: MatrixEndpointContext<GetRoomKeyBackupVersion, Unit, GetRoomKeysBackupVersionResponse>): GetRoomKeysBackupVersionResponse

    /**
     * @see [GetRoomKeyBackupVersionByVersion]
     */
    suspend fun getRoomKeyBackupVersionByVersion(context: MatrixEndpointContext<GetRoomKeyBackupVersionByVersion, Unit, GetRoomKeysBackupVersionResponse>): GetRoomKeysBackupVersionResponse

    /**
     * @see [SetRoomKeyBackupVersion]
     */
    suspend fun setRoomKeyBackupVersion(context: MatrixEndpointContext<SetRoomKeyBackupVersion, SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response>): SetRoomKeyBackupVersion.Response

    /**
     * @see [SetRoomKeyBackupVersionByVersion]
     */
    suspend fun setRoomKeyBackupVersionByVersion(context: MatrixEndpointContext<SetRoomKeyBackupVersionByVersion, SetRoomKeyBackupVersionRequest, Unit>)

    /**
     * @see [DeleteRoomKeyBackupVersion]
     */
    suspend fun deleteRoomKeyBackupVersion(context: MatrixEndpointContext<DeleteRoomKeyBackupVersion, Unit, Unit>)
}