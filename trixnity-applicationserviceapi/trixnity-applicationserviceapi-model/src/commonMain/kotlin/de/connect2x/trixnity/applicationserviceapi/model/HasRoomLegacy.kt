package de.connect2x.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.RoomAliasId

/**
 * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#get_matrixappv1roomsroomalias">matrix spec</a>
 */
@Serializable
@Resource("/rooms/{roomAlias}")
@HttpMethod(GET)
data class HasRoomLegacy(
    @SerialName("roomAlias") val roomAlias: RoomAliasId
) : MatrixEndpoint<Unit, Unit>
