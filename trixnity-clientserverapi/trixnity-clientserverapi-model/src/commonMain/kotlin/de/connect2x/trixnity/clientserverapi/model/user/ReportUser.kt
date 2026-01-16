package de.connect2x.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#post_matrixclientv3usersuseridreport">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/users/{userId}/report")
@HttpMethod(POST)
data class ReportUser(
    @SerialName("userId") val userId: UserId,
) : MatrixEndpoint<ReportUser.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("reason") val reason: String,
    )
}