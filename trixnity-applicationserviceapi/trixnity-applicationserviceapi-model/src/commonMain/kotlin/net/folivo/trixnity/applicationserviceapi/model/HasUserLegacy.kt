package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/application-service-api/#get_matrixappv1usersuserid">matrix spec</a>
 */
@Serializable
@Resource("/users/{userId}")
@HttpMethod(GET)
data class HasUserLegacy(
    @SerialName("userId") val userId: UserId
) : MatrixEndpoint<Unit, Unit>
