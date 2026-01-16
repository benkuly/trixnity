package de.connect2x.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.16/client-server-api/#put_matrixclientv3profileuseridkeyname">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/profile/{userId}/{keyName}")
@HttpMethod(PUT)
data class SetProfileField(
    @SerialName("userId") val userId: UserId,
    @SerialName("keyName") val key: ProfileField.Key<*>,
) : MatrixEndpoint<ProfileField, Unit>