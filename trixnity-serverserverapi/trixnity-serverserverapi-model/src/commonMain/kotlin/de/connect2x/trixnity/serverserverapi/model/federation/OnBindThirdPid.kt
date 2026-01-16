package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#put_matrixfederationv13pidonbind">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/3pid/onbind")
@HttpMethod(PUT)
object OnBindThirdPid : MatrixEndpoint<OnBindThirdPid.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("address")
        val address: String,
        @SerialName("invites")
        val invites: List<ThirdPartyInvite>,
        @SerialName("medium")
        val medium: String,
        @SerialName("mxid")
        val userId: UserId,
    ) {
        @Serializable
        data class ThirdPartyInvite(
            @SerialName("address")
            val address: String,
            @SerialName("medium")
            val medium: String,
            @SerialName("mxid")
            val userId: UserId,
            @SerialName("room_id")
            val roomId: RoomId,
            @SerialName("sender")
            val sender: UserId,
            @SerialName("signed")
            val signed: Signed<UserInfo, String>
        ) {
            @Serializable
            data class UserInfo(
                @SerialName("mxid")
                val userId: UserId,
                @SerialName("token")
                val token: String
            )
        }
    }
}