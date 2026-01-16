package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.Account

interface AccountRepository : MinimalRepository<Long, Account> {
    override fun serializeKey(key: Long): String = key.toString()
}