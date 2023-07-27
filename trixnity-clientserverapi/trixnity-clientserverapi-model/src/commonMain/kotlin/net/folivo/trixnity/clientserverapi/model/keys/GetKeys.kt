package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#post_matrixclientv3keysquery">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/query")
@HttpMethod(POST)
data class GetKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<GetKeys.Request, GetKeys.Response> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json
    ): KSerializer<Response> = CatchingGetKeysResponseSerializer

    @Serializable
    data class Request(
        @SerialName("device_keys")
        val keysFrom: Map<UserId, Set<String>>,
        @SerialName("timeout")
        val timeout: Long?,
    )

    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, JsonElement>?,
        @SerialName("device_keys")
        val deviceKeys: Map<UserId, Map<String, SignedDeviceKeys>>?,
        @SerialName("master_keys")
        val masterKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("self_signing_keys")
        val selfSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("user_signing_keys")
        val userSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
    )
}

