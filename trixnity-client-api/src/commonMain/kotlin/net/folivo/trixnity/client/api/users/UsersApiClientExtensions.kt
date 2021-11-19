package net.folivo.trixnity.client.api.users

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

suspend inline fun <reified C : GlobalAccountDataEventContent> UsersApiClient.getAccountData(
    userId: UserId,
    asUserId: UserId? = null
): C {
    return getAccountData(C::class, userId, asUserId)
}