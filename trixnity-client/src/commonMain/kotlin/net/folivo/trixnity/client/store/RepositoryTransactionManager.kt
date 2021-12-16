package net.folivo.trixnity.client.store

interface RepositoryTransactionManager {
    suspend fun <T> transaction(block: suspend () -> T): T
}