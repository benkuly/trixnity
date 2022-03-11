package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.RoomAliasId

@Serializable
@Resource("/_matrix/app/v1/rooms/{roomAlias}")
data class HasRoom(
    @SerialName("roomAlias") val roomAlias: RoomAliasId
) : MatrixJsonEndpoint<Unit, Unit>() {
    @Transient
    override val method = Get
}
