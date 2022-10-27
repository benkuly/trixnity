package net.folivo.trixnity.client.user

import kotlinx.coroutines.flow.Flow
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

inline fun <reified C : GlobalAccountDataEventContent> UserService.getAccountData(
    key: String = "",
): Flow<C?> {
    return getAccountData(C::class, key)
}