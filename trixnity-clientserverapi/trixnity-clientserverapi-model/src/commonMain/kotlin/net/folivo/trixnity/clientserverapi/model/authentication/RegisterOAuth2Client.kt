package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.client.OAuth2ClientMetadata

/**
 * @see <a href="https://spec.matrix.org/v1.15/client-server-api/#client-registration">matrix spec</a>
 */
@Serializable
@Auth(AuthRequired.NO)
@HttpMethod(HttpMethodType.GET)
object RegisterOAuth2Client : MatrixEndpoint<OAuth2ClientMetadata, OAuth2ClientMetadata>
