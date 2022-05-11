package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.serverserverapi.model.discovery.*

internal fun Route.discoveryApiRoutes(
    handler: DiscoveryApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<GetWellKnown, GetWellKnown.Response>(json, contentMappings) {
            handler.getWellKnown(this)
        }
        matrixEndpoint<GetServerVersion, GetServerVersion.Response>(json, contentMappings) {
            handler.getServerVersion(this)
        }
        matrixEndpoint<GetServerKeys, Signed<ServerKeys, String>>(json, contentMappings) {
            handler.getServerKeys(this)
        }
        matrixEndpoint<QueryServerKeys, QueryServerKeys.Request, QueryServerKeysResponse>(json, contentMappings) {
            handler.queryServerKeys(this)
        }
        matrixEndpoint<QueryServerKeysByServer, QueryServerKeysResponse>(json, contentMappings) {
            handler.queryKeysByServer(this)
        }
    }
}