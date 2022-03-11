package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/rooms/{roomId}/typing/{userId}")
data class SetTyping(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("userId") val userId: UserId,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetTyping.Request, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("typing") val typing: Boolean,
        @SerialName("timeout") val timeout: Int? = null,
    )
}