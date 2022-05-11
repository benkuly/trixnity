package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/_matrix/key/v2/query/{serverName}")
@HttpMethod(GET)
@WithoutAuth
data class QueryServerKeysByServer(
    @SerialName("serverName") val serverName: String,
    @SerialName("minimum_valid_until_ts") val minimumValidUntil: Long? = null,
) : MatrixEndpoint<Unit, QueryServerKeysResponse>