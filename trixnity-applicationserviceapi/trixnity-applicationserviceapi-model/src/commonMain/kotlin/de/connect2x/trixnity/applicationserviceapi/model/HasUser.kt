package de.connect2x.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#get_matrixappv1usersuserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/app/v1/users/{userId}")
@HttpMethod(GET)
data class HasUser(
    @SerialName("userId") val userId: UserId
) : MatrixEndpoint<Unit, Unit>
