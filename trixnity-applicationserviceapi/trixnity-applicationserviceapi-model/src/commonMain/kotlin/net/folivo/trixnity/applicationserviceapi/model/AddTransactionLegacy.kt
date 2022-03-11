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
@Resource("/transactions/{txnId}")
data class AddTransactionLegacy(
    @SerialName("txnId") val txnId: String
) : MatrixJsonEndpoint<AddTransactionLegacy.Request, Unit>() {
    @Transient
    override val method = Put

    @Serializable
    data class Request(
        @SerialName("events")
        val events: List<@Contextual Event<*>>
    )
}
