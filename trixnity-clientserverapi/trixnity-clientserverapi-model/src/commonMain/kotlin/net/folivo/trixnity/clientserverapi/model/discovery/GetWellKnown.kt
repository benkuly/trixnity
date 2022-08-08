package net.folivo.trixnity.clientserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.core.ForceJson
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(GET)
@WithoutAuth
@ForceJson
object GetWellKnown : MatrixEndpoint<Unit, DiscoveryInformation>