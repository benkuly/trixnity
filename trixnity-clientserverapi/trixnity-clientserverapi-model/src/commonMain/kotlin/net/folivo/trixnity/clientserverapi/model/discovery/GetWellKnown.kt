package net.folivo.trixnity.clientserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(GET)
@WithoutAuth
object GetWellKnown : MatrixEndpoint<Unit, DiscoveryInformation>