package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.Account

interface AccountRepository : MinimalRepository<Long, Account> {
    override fun serializeKey(key: Long): String = key.toString()
}