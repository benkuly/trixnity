package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.OlmStore
import net.folivo.trixnity.crypto.sign.SignServiceStore

class ClientSignServiceStore(olmStore: OlmStore, accountStore: AccountStore) :
    SignServiceStore {
    override val olmAccount: String = requireNotNull(olmStore.account.value)
    override val olmPickleKey: String = requireNotNull(accountStore.olmPickleKey.value)
}