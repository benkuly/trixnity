package de.connect2x.trixnity.clientserverapi.model.rtc

import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@MSC4143
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc4143/rtc/transports")
@HttpMethod(GET)
@Auth(AuthRequired.OPTIONAL)
object GetTransports : MatrixEndpoint<Unit, GetTransports.Response> {
    @Serializable data class Response(@SerialName("transports") val transports: List<@Contextual RtcTransport>)
}
