package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.crypto.sign.SignServiceStore

class ClientSignServiceStore(olmCryptoStore: OlmCryptoStore, accountStore: AccountStore) :
    SignServiceStore {
    override val olmAccount: String = requireNotNull(olmCryptoStore.account.value)
    override val olmPickleKey: String = requireNotNull(accountStore.olmPickleKey.value)
}