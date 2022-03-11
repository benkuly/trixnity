package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
@Resource("/_matrix/client/v3/keys/upload")
data class SetKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetKeys.Request, SetKeys.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("device_keys")
        val deviceKeys: SignedDeviceKeys?,
        @SerialName("one_time_keys")
        val oneTimeKeys: Keys?
    )

    @Serializable
    data class Response(
        @SerialName("one_time_key_counts")
        val oneTimeKeyCounts: Map<KeyAlgorithm, Int>,
    )
}