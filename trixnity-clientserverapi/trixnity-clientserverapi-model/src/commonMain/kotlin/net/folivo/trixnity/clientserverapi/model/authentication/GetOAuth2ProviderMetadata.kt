package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.OAuth2ProviderMetadata

/**
 * @see <a href="https://spec.matrix.org/v1.15/client-server-api/#get_matrixclientv1auth_metadata>matrix spec</a>
 */
@Serializable
@Auth(AuthRequired.NO)
@HttpMethod(HttpMethodType.GET)
@Resource("/_matrix/client/v1/auth_metadata")
object GetOAuth2ProviderMetadata : MatrixEndpoint<Unit, OAuth2ProviderMetadata>
