package de.connect2x.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#post_matrixappv1ping">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/app/v1/ping")
@HttpMethod(POST)
object Ping : MatrixEndpoint<Ping.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("transaction_id") val txnId: String? = null
    )
}
