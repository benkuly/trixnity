package de.connect2x.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.keys.Signed

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixkeyv2serverkeyid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/key/v2/server")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetServerKeys : MatrixEndpoint<Unit, Signed<ServerKeys, String>>