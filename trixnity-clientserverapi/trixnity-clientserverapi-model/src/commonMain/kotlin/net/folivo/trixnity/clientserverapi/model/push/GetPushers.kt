package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv3pushers">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushers")
@HttpMethod(GET)
data class GetPushers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetPushers.Response> {
    @Serializable
    data class Response(
        @SerialName("pushers") val devices: List<Pusher>,
    ) {
        @Serializable
        data class Pusher(
            @SerialName("app_display_name")
            val appDisplayName: String,
            @SerialName("app_id")
            val appId: String,
            @SerialName("data")
            val data: PusherData,
            @SerialName("device_display_name")
            val deviceDisplayName: String,
            @SerialName("kind")
            val kind: String,
            @SerialName("lang")
            val lang: String,
            @SerialName("profile_tag")
            val profileTag: String? = null,
            @SerialName("pushkey")
            val pushkey: String,
        )
    }
}