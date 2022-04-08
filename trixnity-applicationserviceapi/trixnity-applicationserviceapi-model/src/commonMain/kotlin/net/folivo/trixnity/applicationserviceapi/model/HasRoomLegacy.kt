package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.RoomAliasId

@Serializable
@Resource("/rooms/{roomAlias}")
@HttpMethod(GET)
data class HasRoomLegacy(
    @SerialName("roomAlias") val roomAlias: RoomAliasId
) : MatrixEndpoint<Unit, Unit>
