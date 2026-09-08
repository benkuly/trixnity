package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MatrixEndpoint
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@MSC4140
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc4140/delayed_events/{delay_id}/{action}")
@HttpMethod(POST)
data class DelayedEventAction(@SerialName("delay_id") val delayId: String, @SerialName("action") val action: Action) :
    MatrixEndpoint<Unit, Unit> {

    @Serializable
    enum class Action {
        @SerialName("send") SEND,
        @SerialName("cancel") CANCEL,
        @SerialName("restart") RESTART,
    }
}
