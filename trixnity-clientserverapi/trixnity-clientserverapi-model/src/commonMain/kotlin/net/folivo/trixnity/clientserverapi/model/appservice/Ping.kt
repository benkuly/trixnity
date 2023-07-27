package net.folivo.trixnity.clientserverapi.model.appservice

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.7/application-service-api/#post_matrixclientv1appserviceappserviceidping">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/appservice/{appserviceId}/ping")
@HttpMethod(POST)
data class Ping(
    @SerialName("appserviceId") val appserviceId: String,
) : MatrixEndpoint<Ping.Request, Ping.Response> {

    @Serializable
    data class Request(
        @SerialName("transaction_id") val txnId: String? = null
    )

    @Serializable
    data class Response(
        @SerialName("duration_ms")
        val durationMs: Long,
    )
}