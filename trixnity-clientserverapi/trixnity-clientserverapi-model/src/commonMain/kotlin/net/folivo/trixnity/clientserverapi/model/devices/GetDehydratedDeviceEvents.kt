package net.folivo.trixnity.clientserverapi.model.devices

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.ExperimentalTrixnityApi
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3devicesdeviceid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/dehydrated_device/{device_id}/events")
@HttpMethod(GET)
@ExperimentalTrixnityApi
data class GetDehydratedDeviceEvents( // FIXME test
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val asUserId: UserId? = null
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