package de.connect2x.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/filter/{filterId}")
@HttpMethod(GET)
data class GetFilter(
    @SerialName("userId") val userId: UserId,
    @SerialName("filterId") val filterId: String,
) : MatrixEndpoint<Unit, Filters>