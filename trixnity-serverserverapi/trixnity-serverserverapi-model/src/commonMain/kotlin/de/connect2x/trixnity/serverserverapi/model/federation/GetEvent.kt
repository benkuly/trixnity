package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1eventeventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/event/{eventId}")
@HttpMethod(GET)
data class GetEvent(
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Unit, PduTransaction>