package net.folivo.trixnity.client.store.repository

interface RepositoryTransactionManager {
    suspend fun <T> transaction(block: suspend () -> T): T
}