package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GlobalAccountDataEventContentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#put_matrixclientv3useruseridaccount_datatype">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/account_data/{type}")
@HttpMethod(PUT)
data class SetGlobalAccountData(
    @SerialName("userId") val userId: UserId,
    @SerialName("type") val type: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<GlobalAccountDataEventContent, Unit> {
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json
    ): KSerializer<GlobalAccountDataEventContent> =
        GlobalAccountDataEventContentSerializer(type, mappings.globalAccountData)
}