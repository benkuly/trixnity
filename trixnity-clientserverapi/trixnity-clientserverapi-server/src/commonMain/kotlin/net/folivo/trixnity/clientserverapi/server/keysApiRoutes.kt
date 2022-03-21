package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.RoomKeyBackup
import net.folivo.trixnity.core.model.keys.RoomKeyBackupData
import net.folivo.trixnity.core.model.keys.RoomsKeyBackup
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.keysApiRoutes(
    handler: KeysApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<SetKeys, SetKeys.Request, SetKeys.Response>(json, contentMappings) {
            handler.setKeys(this)
        }
        matrixEndpoint<GetKeys, GetKeys.Request, GetKeys.Response>(json, contentMappings) {
            handler.getKeys(this)
        }
        matrixEndpoint<ClaimKeys, ClaimKeys.Request, ClaimKeys.Response>(json, contentMappings) {
            handler.claimKeys(this)
        }
        matrixEndpoint<GetKeyChanges, GetKeyChanges.Response>(json, contentMappings) {
            handler.getKeyChanges(this)
        }
        matrixUIAEndpoint<SetCrossSigningKeys, SetCrossSigningKeys.Request, Unit>(json, contentMappings) {
            handler.setCrossSigningKeys(this)
        }
        matrixEndpoint<AddSignatures, Map<UserId, Map<String, JsonElement>>, AddSignatures.Response>(
            json,
            contentMappings
        ) {
            handler.addSignatures(this)
        }
        matrixEndpoint<GetRoomsKeyBackup, RoomsKeyBackup>(json, contentMappings) {
            handler.getRoomsKeyBackup(this)
        }
        matrixEndpoint<GetRoomKeyBackup, RoomKeyBackup>(json, contentMappings) {
            handler.getRoomKeyBackup(this)
        }
        matrixEndpoint<GetRoomKeyBackupData, RoomKeyBackupData>(json, contentMappings) {
            handler.getRoomKeyBackupData(this)
        }
        matrixEndpoint<SetRoomsKeyBackup, RoomsKeyBackup, SetRoomKeysResponse>(json, contentMappings) {
            handler.setRoomsKeyBackup(this)
        }
        matrixEndpoint<SetRoomKeyBackup, RoomKeyBackup, SetRoomKeysResponse>(json, contentMappings) {
            handler.setRoomKeyBackup(this)
        }
        matrixEndpoint<SetRoomKeyBackupData, RoomKeyBackupData, SetRoomKeysResponse>(json, contentMappings) {
            handler.setRoomKeyBackupData(this)
        }
        matrixEndpoint<DeleteRoomsKeyBackup, DeleteRoomKeysResponse>(json, contentMappings) {
            handler.deleteRoomsKeyBackup(this)
        }
        matrixEndpoint<DeleteRoomKeyBackup, DeleteRoomKeysResponse>(json, contentMappings) {
            handler.deleteRoomKeyBackup(this)
        }
        matrixEndpoint<DeleteRoomKeyBackupData, DeleteRoomKeysResponse>(json, contentMappings) {
            handler.deleteRoomKeyBackupData(this)
        }
        matrixEndpoint<GetRoomKeyBackupVersion, GetRoomKeysBackupVersionResponse>(json, contentMappings) {
            handler.getRoomKeyBackupVersion(this)
        }
        matrixEndpoint<GetRoomKeyBackupVersionByVersion, GetRoomKeysBackupVersionResponse>(json, contentMappings) {
            handler.getRoomKeyBackupVersionByVersion(this)
        }
        matrixEndpoint<SetRoomKeyBackupVersion, SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response>(
            json,
            contentMappings
        ) {
            handler.setRoomKeyBackupVersion(this)
        }
        matrixEndpoint<SetRoomKeyBackupVersionByVersion, SetRoomKeyBackupVersionRequest>(json, contentMappings) {
            handler.setRoomKeyBackupVersionByVersion(this)
        }
        matrixEndpoint<DeleteRoomKeyBackupVersion>(json, contentMappings) {
            handler.deleteRoomKeyBackupVersion(this)
        }
    }
}