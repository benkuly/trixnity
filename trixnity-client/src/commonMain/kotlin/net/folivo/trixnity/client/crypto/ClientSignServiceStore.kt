package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.crypto.sign.SignServiceStore

class ClientSignServiceStore(private val store: Store) : SignServiceStore {
    override suspend fun getOlmAccount(): String? = store.olm.account.value
}