package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/pushers/set")
data class SetPushers(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetPushers.Request, Unit>() {
    @Transient
    override val method = Post

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