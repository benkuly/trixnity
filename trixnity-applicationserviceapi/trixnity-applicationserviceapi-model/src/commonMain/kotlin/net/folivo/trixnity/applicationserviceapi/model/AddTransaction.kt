package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent

/**
 * @see <a href="https://spec.matrix.org/v1.7/application-service-api/#put_matrixappv1transactionstxnid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/app/v1/transactions/{txnId}")
@HttpMethod(PUT)
data class AddTransaction(
    @SerialName("txnId") val txnId: String
) : MatrixEndpoint<AddTransaction.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("events")
        val events: List<@Contextual RoomEvent<*>>
    )
}
