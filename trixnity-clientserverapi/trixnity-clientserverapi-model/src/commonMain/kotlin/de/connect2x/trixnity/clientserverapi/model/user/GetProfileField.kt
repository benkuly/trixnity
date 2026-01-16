package de.connect2x.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#get_matrixclientv3profileuseridkeyname">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/{keyName}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class GetProfileField(
    @SerialName("userId") val userId: UserId,
    @SerialName("keyName") val key: ProfileField.Key<*>,
) : MatrixEndpoint<Unit, ProfileField>