package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.users.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.usersApiRoutes(
    handler: UsersApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    authenticate {
        matrixEndpoint<GetDisplayName, Unit, GetDisplayName.Response>(json, contentMappings) {
            handler.getDisplayName(this)
        }
        matrixEndpoint<SetDisplayName, SetDisplayName.Request, Unit>(json, contentMappings) {
            handler.setDisplayName(this)
        }
        matrixEndpoint<GetAvatarUrl, Unit, GetAvatarUrl.Response>(json, contentMappings) {
            handler.getAvatarUrl(this)
        }
        matrixEndpoint<SetAvatarUrl, SetAvatarUrl.Request, Unit>(json, contentMappings) {
            handler.setAvatarUrl(this)
        }
        matrixEndpoint<GetProfile, Unit, GetProfile.Response>(json, contentMappings) {
            handler.getProfile(this)
        }
        matrixEndpoint<GetPresence, Unit, PresenceEventContent>(json, contentMappings) {
            handler.getPresence(this)
        }
        matrixEndpoint<SetPresence, SetPresence.Request, Unit>(json, contentMappings) {
            handler.setPresence(this)
        }
        matrixEndpoint<SendToDevice, SendToDevice.Request, Unit>(json, contentMappings) {
            handler.sendToDevice(this)
        }
        matrixEndpoint<GetFilter, Unit, Filters>(json, contentMappings) {
            handler.getFilter(this)
        }
        matrixEndpoint<SetFilter, Filters, SetFilter.Response>(json, contentMappings) {
            handler.setFilter(this)
        }
        matrixEndpoint<GetGlobalAccountData, Unit, GlobalAccountDataEventContent>(json, contentMappings) {
            handler.getAccountData(this)
        }
        matrixEndpoint<SetGlobalAccountData, GlobalAccountDataEventContent, Unit>(json, contentMappings) {
            handler.setAccountData(this)
        }
        matrixEndpoint<SearchUsers, SearchUsers.Request, SearchUsers.Response>(json, contentMappings) {
            handler.searchUsers(this)
        }

    }
}