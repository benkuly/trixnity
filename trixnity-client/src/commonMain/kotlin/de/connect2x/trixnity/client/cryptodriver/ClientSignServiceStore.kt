package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.crypto.sign.SignServiceStore

class ClientSignServiceStore(private val olmCryptoStore: OlmCryptoStore, private val accountStore: AccountStore) :
    SignServiceStore {
    override suspend fun getOlmAccount(): String = checkNotNull(olmCryptoStore.getOlmAccount())
    override suspend fun getOlmPickleKey(): String? = checkNotNull(accountStore.getAccount()).olmPickleKey
}