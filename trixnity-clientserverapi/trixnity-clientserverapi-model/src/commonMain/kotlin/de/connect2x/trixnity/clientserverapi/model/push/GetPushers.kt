package de.connect2x.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3pushers">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushers")
@HttpMethod(GET)
data object GetPushers : MatrixEndpoint<Unit, GetPushers.Response> {
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