package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MSC2965
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.15/client-server-api/#get_matrixclientv1auth_metadata>matrix spec</a>
 */
@Serializable
@Auth(AuthRequired.NO)
@HttpMethod(HttpMethodType.GET)
@Resource("/_matrix/client/v1/auth_metadata")
object GetOAuth2ServerMetadata : MatrixEndpoint<Unit, OAuth2ServerMetadata>

/**
 * @see <a href="https://github.com/sandhose/matrix-spec-proposals/blob/706f0bb59a9ade3fc32e379388cfca52003986e7/proposals/2965-auth-metadata.md>MSC2965</a>
 */
@MSC2965
@Serializable
@Auth(AuthRequired.NO)
@HttpMethod(HttpMethodType.GET)
@Resource("/_matrix/client/unstable/org.matrix.msc2965/auth_metadata")
object GetOAuth2ServerMetadataUnstable : MatrixEndpoint<Unit, OAuth2ServerMetadata>
