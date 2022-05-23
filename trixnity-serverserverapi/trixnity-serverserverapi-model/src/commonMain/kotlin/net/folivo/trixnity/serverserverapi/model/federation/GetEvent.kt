package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId

/**
 * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#get_matrixfederationv1eventeventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/event/{eventId}")
@HttpMethod(GET)
data class GetEvent(
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Unit, PduTransaction>