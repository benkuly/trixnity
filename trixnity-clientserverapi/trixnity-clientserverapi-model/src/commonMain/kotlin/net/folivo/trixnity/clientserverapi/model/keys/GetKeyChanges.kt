package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/keys/changes")
data class GetKeyChanges(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetKeyChanges.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("changed")
        val changed: Set<UserId>,
        @SerialName("left")
        val left: Set<UserId>
    )
}