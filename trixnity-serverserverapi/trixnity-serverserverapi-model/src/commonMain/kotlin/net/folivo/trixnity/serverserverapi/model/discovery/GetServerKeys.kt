package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixkeyv2serverkeyid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/key/v2/server")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetServerKeys : MatrixEndpoint<Unit, Signed<ServerKeys, String>>