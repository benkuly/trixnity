package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.serverApiRoutes(
    handler: ServerApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    authenticate {
        matrixEndpoint<GetVersions, Unit, GetVersions.Response>(json, contentMappings) {
            handler.getVersions(this)
        }
        matrixEndpoint<GetCapabilities, Unit, GetCapabilities.Response>(json, contentMappings) {
            handler.getCapabilities(this)
        }
        matrixEndpoint<Search, Search.Request, Search.Response>(json, contentMappings) {
            handler.search(this)
        }
        matrixEndpoint<WhoIs, WhoIs.Response>(json, contentMappings) {
            handler.whoIs(this)
        }
    }
}