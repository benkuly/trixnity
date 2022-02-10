package net.folivo.trixnity.client.api.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetPushersRequest(
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
    val kind: String,
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