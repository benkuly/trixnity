package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(HttpMethodType.GET)
@WithoutAuth
object GetWellKnown : MatrixEndpoint<Unit, DiscoveryInformation>