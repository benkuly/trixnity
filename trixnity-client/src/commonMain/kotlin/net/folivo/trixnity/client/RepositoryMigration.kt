package net.folivo.trixnity.client

interface RepositoryMigration {
    suspend fun run()
}