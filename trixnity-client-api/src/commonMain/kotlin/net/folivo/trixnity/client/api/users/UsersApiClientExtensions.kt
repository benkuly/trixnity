package net.folivo.trixnity.client.api.users

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

suspend inline fun <reified C : GlobalAccountDataEventContent> UsersApiClient.getAccountData(
    userId: MatrixId.UserId,
    asUserId: MatrixId.UserId? = null
): C {
    return getAccountData(C::class, userId, asUserId)
}