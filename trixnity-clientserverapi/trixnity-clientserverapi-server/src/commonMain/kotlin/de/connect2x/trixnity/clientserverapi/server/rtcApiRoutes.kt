package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

@MSC4195
@MSC4143
internal fun Route.rtcApiRoutes(handler: RtcApiHandler, json: Json, contentMappings: EventContentSerializerMappings) {
    matrixEndpoint(json, contentMappings, handler::getTransports)
    matrixEndpoint(json, contentMappings, handler::getLiveKitToken)
    matrixEndpoint(json, contentMappings, handler::delegateDelayedLeave)
}
