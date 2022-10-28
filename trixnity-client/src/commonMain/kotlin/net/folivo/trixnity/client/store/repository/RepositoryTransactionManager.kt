package net.folivo.trixnity.client.store.repository

interface RepositoryTransactionManager {
    suspend fun <T> readTransaction(block: suspend () -> T): T
    suspend fun <T> writeTransaction(block: suspend () -> T): T
}