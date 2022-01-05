package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

suspend inline fun <reified C : GlobalAccountDataEventContent> UserService.getAccountData(
    key: String = "",
    scope: CoroutineScope
): StateFlow<C?> {
    return getAccountData(C::class, key, scope)
}