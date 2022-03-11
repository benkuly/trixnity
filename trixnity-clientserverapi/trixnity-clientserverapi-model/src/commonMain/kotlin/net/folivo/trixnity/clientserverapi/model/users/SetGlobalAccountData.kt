package net.folivo.trixnity.clientserverapi.model.users

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

@Serializable
@Resource("/_matrix/client/v3/user/{userId}/account_data/{type}")
data class SetGlobalAccountData<C : GlobalAccountDataEventContent>(
    @SerialName("userId") val userId: UserId,
    @SerialName("type") val type: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<C, Unit>() {
    @Transient
    override val method = Put
}