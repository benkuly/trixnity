package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/pushers/set")
@HttpMethod(POST)
data class SetPushers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetPushers.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("app_display_name")
        val appDisplayName: String,
        @SerialName("app_id")
        val appId: String,
        @SerialName("append")
        val append: Boolean? = null,
        @SerialName("data")
        val data: PusherData,
        @SerialName("device_display_name")
        val deviceDisplayName: String,
        @SerialName("kind")
        val kind: String? = null,
        @SerialName("lang")
        val lang: String,
        @SerialName("profile_tag")
        val profileTag: String? = null,
        @SerialName("pushkey")
        val pushkey: String,
    ) {
        @Serializable
        data class PusherData(
            @SerialName("format")
            val format: String? = null,
            @SerialName("url")
            val url: String? = null
        )
    }
}