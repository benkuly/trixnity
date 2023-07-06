package net.folivo.trixnity.client.store.repository

interface OlmAccountRepository : MinimalRepository<Long, String> {
    override fun serializeKey(key: Long): String = key.toString()
}