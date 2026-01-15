package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.keyApiRoutes(
    handler: KeyApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::setKeys)
    matrixEndpoint(json, contentMappings, handler::getKeys)
    matrixEndpoint(json, contentMappings, handler::claimKeys)
    matrixEndpoint(json, contentMappings, handler::getKeyChanges)
    matrixUIAEndpoint(json, contentMappings, handler::setCrossSigningKeys)
    matrixEndpoint(json, contentMappings, handler::addSignatures)
    matrixEndpoint(json, contentMappings, handler::getRoomsKeyBackup)
    matrixEndpoint(json, contentMappings, handler::getRoomKeyBackup)
    matrixEndpoint(json, contentMappings, handler::getRoomKeyBackupData)
    matrixEndpoint(json, contentMappings, handler::setRoomsKeyBackup)
    matrixEndpoint(json, contentMappings, handler::setRoomKeyBackup)
    matrixEndpoint(json, contentMappings, handler::setRoomKeyBackupData)
    matrixEndpoint(json, contentMappings, handler::deleteRoomsKeyBackup)
    matrixEndpoint(json, contentMappings, handler::deleteRoomKeyBackup)
    matrixEndpoint(json, contentMappings, handler::deleteRoomKeyBackupData)
    matrixEndpoint(json, contentMappings, handler::getRoomKeyBackupVersion)
    matrixEndpoint(json, contentMappings, handler::getRoomKeyBackupVersionByVersion)
    matrixEndpoint(json, contentMappings, handler::setRoomKeyBackupVersion)
    matrixEndpoint(json, contentMappings, handler::setRoomKeyBackupVersionByVersion)
    matrixEndpoint(json, contentMappings, handler::deleteRoomKeyBackupVersion)
}