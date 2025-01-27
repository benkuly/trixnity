package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixkeyv2queryservernamekeyid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/key/v2/query/{serverName}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class QueryServerKeysByServer(
    @SerialName("serverName") val serverName: String,
    @SerialName("minimum_valid_until_ts") val minimumValidUntil: Long? = null,
) : MatrixEndpoint<Unit, QueryServerKeysResponse>