package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3presenceuseridstatus">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/presence/{userId}/status")
@HttpMethod(PUT)
data class SetPresence(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<SetPresence.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("presence") val presence: Presence,
        @SerialName("status_msg") val statusMessage: String?
    )
}