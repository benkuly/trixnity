package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3useruseridfilterfilterid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/filter/{filterId}")
@HttpMethod(GET)
data class GetFilter(
    @SerialName("userId") val userId: UserId,
    @SerialName("filterId") val filterId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Filters>