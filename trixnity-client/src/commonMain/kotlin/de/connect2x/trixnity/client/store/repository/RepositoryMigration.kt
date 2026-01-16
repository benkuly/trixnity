package de.connect2x.trixnity.client.store.repository

interface RepositoryMigration {
    suspend fun run()
}