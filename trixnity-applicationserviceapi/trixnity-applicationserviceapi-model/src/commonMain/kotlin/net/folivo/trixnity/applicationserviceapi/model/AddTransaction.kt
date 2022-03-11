package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.events.Event

@Serializable
@Resource("/_matrix/app/v1/transactions/{txnId}")
data class AddTransaction(
    @SerialName("txnId") val txnId: String
) : MatrixJsonEndpoint<AddTransaction.Request, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("events")
        val events: List<@Contextual Event<*>>
    )
}
