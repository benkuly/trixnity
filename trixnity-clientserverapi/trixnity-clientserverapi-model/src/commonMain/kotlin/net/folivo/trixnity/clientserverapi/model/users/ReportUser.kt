package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

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