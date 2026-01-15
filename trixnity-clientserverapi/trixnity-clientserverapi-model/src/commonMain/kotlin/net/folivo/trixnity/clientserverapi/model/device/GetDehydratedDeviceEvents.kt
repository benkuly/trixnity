package net.folivo.trixnity.clientserverapi.model.device

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc3814.v1/dehydrated_device/{device_id}/events")
@HttpMethod(POST)
@MSC3814
data class GetDehydratedDeviceEvents(
    @SerialName("device_id") val deviceId: String,
) : MatrixEndpoint<GetDehydratedDeviceEvents.Request, GetDehydratedDeviceEvents.Response> {
    @Serializable
    data class Request(
        @SerialName("next_batch")
        val nextBatch: String? = null,
    )

    @Serializable
    data class Response(
        @SerialName("next_batch")
        val nextBatch: String,
        @SerialName("events")
        val events: List<@Contextual ToDeviceEvent<*>>,
    )
}