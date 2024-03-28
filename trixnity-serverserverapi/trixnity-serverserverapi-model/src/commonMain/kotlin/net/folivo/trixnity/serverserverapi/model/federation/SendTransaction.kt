package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#put_matrixfederationv1sendtxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/send/{txnId}")
@HttpMethod(HttpMethodType.PUT)
data class SendTransaction(
    @SerialName("txnId") val txnId: String,
) : MatrixEndpoint<SendTransaction.Request, SendTransaction.Response> {
    @Serializable
    data class Request(
        @SerialName("edus") val edus: List<@Contextual EphemeralDataUnit<*>>? = null,
        @SerialName("origin") val origin: String,
        @SerialName("origin_server_ts") val originTimestamp: Long,
        @SerialName("pdus") val pdus: List<SignedPersistentDataUnit<*>>,
    )

    @Serializable
    data class Response(
        @SerialName("pdus") val pdus: Map<EventId, PDUProcessingResult>,
    ) {
        @Serializable
        data class PDUProcessingResult(
            @SerialName("error") val error: String? = null
        )
    }
}