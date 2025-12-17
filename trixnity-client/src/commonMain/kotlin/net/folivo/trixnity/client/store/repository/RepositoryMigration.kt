package net.folivo.trixnity.client.store.repository

interface RepositoryMigration {
    suspend fun run()
}