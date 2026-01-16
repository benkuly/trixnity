package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1queryprofile">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/query/profile")
@HttpMethod(GET)
data class QueryProfile(
    @SerialName("user_id") val userId: UserId,
    @SerialName("field") val field: Field? = null
) : MatrixEndpoint<Unit, QueryProfile.Response> {

    @Serializable
    enum class Field {
        @SerialName("displayname")
        DISPLAYNNAME,

        @SerialName("avatar_url")
        AVATAR_URL,
    }

    @Serializable
    data class Response(
        @SerialName("avatar_url") val avatarUrl: String? = null,
        @SerialName("displayname") val displayname: String? = null,
    )
}