package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.crypto.sign.SignServiceStore

class ClientSignServiceStore(private val olmCryptoStore: OlmCryptoStore, private val accountStore: AccountStore) :
    SignServiceStore {
    override suspend fun getOlmAccount(): String = checkNotNull(olmCryptoStore.getOlmAccount())
    override suspend fun getOlmPickleKey(): String = checkNotNull(accountStore.getAccount()?.olmPickleKey)
}