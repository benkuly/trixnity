package net.folivo.trixnity.applicationserviceapi.model

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/app/v1/users/{userId}")
data class HasUser(
    @SerialName("userId") val userId: UserId
) : MatrixJsonEndpoint<Unit, Unit>() {
    @Transient
    override val method = Get
}
