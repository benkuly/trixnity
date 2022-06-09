package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

suspend inline fun <reified C : GlobalAccountDataEventContent> IUserService.getAccountData(
    key: String = "",
    scope: CoroutineScope
): Flow<C?> {
    return getAccountData(C::class, key, scope)
}

suspend inline fun <reified C : GlobalAccountDataEventContent> IUserService.getAccountData(
    key: String = "",
): C? {
    return getAccountData(C::class, key)
}