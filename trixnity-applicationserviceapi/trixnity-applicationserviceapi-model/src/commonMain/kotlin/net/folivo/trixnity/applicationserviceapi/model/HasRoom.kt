package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomAliasId

/**
 * @see <a href="https://spec.matrix.org/v1.3/application-service-api/#get_matrixappv1roomsroomalias">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/app/v1/rooms/{roomAlias}")
@HttpMethod(GET)
data class HasRoom(
    @SerialName("roomAlias") val roomAlias: RoomAliasId
) : MatrixEndpoint<Unit, Unit>
