package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3adminwhoisuserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/admin/whois/{userId}")
@HttpMethod(GET)
data class WhoIs(
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, WhoIs.Response> {
    @Serializable
    data class Response(
        @SerialName("user_id") val userId: UserId? = null,
        @SerialName("devices") val devices: Map<String, DeviceInfo>? = null
    ) {
        @Serializable
        data class DeviceInfo(
            @SerialName("sessions") val sessions: Set<SessionInfo>? = null
        ) {
            @Serializable
            data class SessionInfo(
                @SerialName("connections") val connections: Set<ConnectionInfo>? = null
            ) {
                @Serializable
                data class ConnectionInfo(
                    @SerialName("ip") val ip: String? = null,
                    @SerialName("last_seen") val lastSeen: Long? = null,
                    @SerialName("user_agent") val userAgent: String? = null,
                )
            }
        }
    }
}