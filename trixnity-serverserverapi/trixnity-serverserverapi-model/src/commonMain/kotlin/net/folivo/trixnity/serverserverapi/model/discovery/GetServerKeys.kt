package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
@Resource("/_matrix/key/v2/server")
@HttpMethod(GET)
@WithoutAuth
object GetServerKeys : MatrixEndpoint<Unit, Signed<ServerKeys, String>>