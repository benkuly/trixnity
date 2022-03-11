package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/room_keys/version")
data class SetRoomKeyBackupVersion(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetRoomKeyBackupVersionRequest, SetRoomKeyBackupVersion.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Response(
        @SerialName("version")
        val version: String,
    )
}