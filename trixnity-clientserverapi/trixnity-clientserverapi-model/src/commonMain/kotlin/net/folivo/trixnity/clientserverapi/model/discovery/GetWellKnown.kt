package net.folivo.trixnity.clientserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethodType.GET

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
@ForceJson
object GetWellKnown : MatrixEndpoint<Unit, DiscoveryInformation>