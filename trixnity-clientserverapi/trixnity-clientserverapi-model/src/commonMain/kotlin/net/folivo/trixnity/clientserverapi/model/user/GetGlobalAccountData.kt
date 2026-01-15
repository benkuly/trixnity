package net.folivo.trixnity.clientserverapi.model.user

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3useruseridaccount_datatype">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/user/{userId}/account_data/{type}")
@HttpMethod(GET)
data class GetGlobalAccountData(
    @SerialName("userId") val userId: UserId,
    @SerialName("type") val type: String,
) : MatrixEndpoint<Unit, GlobalAccountDataEventContent> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: GlobalAccountDataEventContent?
    ): KSerializer<GlobalAccountDataEventContent> =
        mappings.globalAccountData.contentSerializer(type, value)
}