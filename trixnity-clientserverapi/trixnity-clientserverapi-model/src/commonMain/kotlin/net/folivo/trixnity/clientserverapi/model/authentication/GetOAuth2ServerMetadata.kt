package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.ServerMetadata
import net.folivo.trixnity.core.*

/**
 * @see <a href="https://spec.matrix.org/v1.15/client-server-api/#get_matrixclientv1auth_metadata>matrix spec</a>
 */
@Serializable
@Auth(AuthRequired.NO)
@HttpMethod(HttpMethodType.GET)
@Resource("/_matrix/client/v1/auth_metadata")
object GetOAuth2ServerMetadata : MatrixEndpoint<Unit, ServerMetadata>
