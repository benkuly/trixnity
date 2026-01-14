package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#delete_matrixclientv3profileuseridkeyname">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/{keyName}")
@HttpMethod(DELETE)
data class DeleteProfileField(
    @SerialName("userId") val userId: UserId,
    @SerialName("keyName") val key: ProfileField.Key<*>,
) : MatrixEndpoint<Unit, Unit>